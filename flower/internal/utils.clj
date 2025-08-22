(ns flower.internal.utils
  (:require
   [babashka.fs :as fs]
   [babashka.process :as ps]
   [clojure.set :as set :refer [union]]
   [clojure.string :as str]
   [instaparse.core :as insta]))

(def ^:dynamic *site* ".")
(def ^:dynamic *cmd* " <CLI parsing>")
(def ^:private bs "\\")

(defmacro reexport [& syms]
  (let [defs (for [sym syms]
               `(intern *ns* '~sym ~sym))]
    `(do ~@defs)))

(defmacro fmt [^String string]
  (let [-re #"\$\{(.*?)\}"
        fstr (clojure.string/replace string -re "%s")
        fargs (map #(read-string (second %)) (re-seq -re string))]
    `(format ~fstr ~@fargs)))

(defn eprint [& msg]
  (binding [*out* *err*]
    (apply print msg)))
(defn eprintln [& msg]
  (binding [*out* *err*]
    (apply println msg)))
(defn eprn [& msg]
  (binding [*out* *err*]
    (apply prn msg)))
(defn inspect [x] (eprn x) x)

(defn warn [& msg]
  (apply eprintln (fmt "flower${*cmd*}: warning:") msg))
(defn error [& msg]
  (apply eprintln (fmt "flower${*cmd*}: error:") msg))
(defn fatal [& msg]
  (throw (ex-info
           (apply str (interpose " " msg))
           {:flower/exit true})))

(defn run [opts & rest]
  (let [[opts rest] (if (map? opts)
                      [(assoc opts :dir *site*) rest]
                      [{:dir *site*} (into opts rest)])]
    (if (sequential? rest)
      (apply ps/shell opts rest)
      (ps/shell opts rest))))

(defn run-non-fatal
  "Like `run`, but if the process fails, print an error instead of throwing an exception.
  You can check if the process failed because you'll get `nil` instead of a process record."
  [opts & rest]
  (try (apply run opts rest)
       (catch clojure.lang.ExceptionInfo e
         (if (= (:type (ex-data e)) :babashka.process/error)
           (let [cmd (if (map? opts) (str/join " " rest) opts)]
               (error (fmt "failed to run ${cmd}: exit code") (:exit (ex-data e))))
           (throw e)))))

(defn strip-prefix
  [s pre]
  (let [quoted (java.util.regex.Pattern/quote pre)
        prefix (re-pattern (str "^" quoted))]
    (str/replace-first s prefix "")))

(defn remove-parent
  "Given an file path, remove the first N directories.
   If N is not given, assume N=1."
  ([path] (remove-parent path 1))
  ([path n] (->> path fs/components (drop n) (apply fs/path))))

(defn remove-ext
  [path]
  (first (fs/split-ext path)))

; you can see all fields with `(into {} err)`
; TODO: see if we can use :total to get a partial parse
(defn- render-parse-error [ex description]
  (let [base (fmt "failed to parse ${description}:\n" )
        lines (->> ex pr-str str/split-lines)
        indented (str/join "\n" (map #(str "  " %) lines))]
    (str base indented)))

(defn parse-or-fatal
  [parser input description]
  (let [ev (parser input)]
    (if-let [err (insta/get-failure ev)]
      (fatal (render-parse-error err description))
      ev)))

(defn escape-shell
  "the world's WORST shell escaper"
  [s]
  (str "'" (str/escape s {\' "'\\''"}) "'"))

(def ^:private insta-bs (str bs bs))
(def ^:private insta-qt (str bs "'"))
(def ^:private ninja-parser
  ; NOTE: ninja never emits `\` outside of a quoted atom.
  (insta/parser (fmt
    "Start = Atom*
     Atom = Unquoted | Quoted | QuoteMark
     Unquoted = #'[^${insta-qt}${insta-bs}]+'
     Quoted = <'${insta-qt}'> #'[^${insta-qt}]+' <'${insta-qt}'>
     QuoteMark = <'${insta-bs}'> '${insta-qt}'")))
(defn shlex-ninja
  "This is a REALLY REALLY STUPID implementation of shlex that only works for syntax that ninja emits."
  [path]
  (let [parsed (ninja-parser path)]
    (insta/transform
      {:Start str
       :Atom identity
       :Unquoted identity
       :Quoted identity
       :QuoteMark identity}
      parsed)))

(defn parse-ninja
  ([args] (parse-ninja args false))
  ([args quoted]
  (let [out (:out (run {:out :string} args))
        ; ;-;;;;;
        ; https://github.com/ninja-build/ninja/issues/2658
        parse-quoted #(parse-or-fatal shlex-ninja % (fmt "`${args}`"))
        parse (if quoted parse-quoted identity)]
    ; handle empty string
    (if (seq out)
      (map parse (str/split out #"\n"))
      []))))

(defn escape-ninja
  "Escape a string for use as a ninja file path.
   See https://ninja-build.org/manual.html#ref_lexer"
  [s] 
  ; https://github.com/ninja-build/ninja/blob/370edd49a47379d0c3ff0c0ae9d825e627fd37c3/misc/ninja_syntax.py#L30
  (-> s str
      ; NOTE: $ has to come first
      (str/replace "$" "$$")
      (str/replace "\n" "$\n")
      (str/replace " " "$ ")
      (str/replace ":" "$:")))

(defn join-ninja
  "Given a list of file paths, format them as a ninja dependency set."
  [xs]
  (let [xs (if (or (nil? xs) (sequential? xs))
             xs
             [xs])]
    (->> xs (map escape-ninja) (str/join " "))))

; https://groups.google.com/g/clojure/c/UdFLYjLvNRs/m/8fd9fvNur6cJ
(defn merge-deep [& xs]
  (cond
    (every? map? xs) (apply merge-with merge-deep xs)
    (every? set? xs) (apply union xs)
    (every? sequential? xs) (apply concat xs)
    :else (last xs)))

; https://gist.github.com/erez-rabih/038844d6c67ee85401d9c074ea5bfa71
(defn split-map [m & ks]
  [(apply dissoc m ks)
   (select-keys m ks)])

; https://github.com/clojure/clojure-contrib/blob/b8d2743d3a89e13fc9deb2844ca2167b34aaa9b6/src/main/clojure/clojure/contrib/seq.clj#L51
(defn enumerate
  "Returns a lazy sequence of [index, item] pairs, where items come
  from 's' and indexes count up from zero.

  (indexed '(a b c d))  =>  ([0 a] [1 b] [2 c] [3 d])"
  [s]
  (map vector (iterate inc 0) s))

(defn symmmetric-difference [A B]
  (union (set/difference A B) (set/difference B A)))

; TODO: i think this won't return the initial `ex` :(
(defn ex-causes [ex]
  (iteration ex-cause :initk ex))

(def env System/getenv)

(defn home [] (System/getProperty "user.home"))

; https://github.com/NetLogo/NetLogo/blob/de24f273963a18cf42c257301f2921b90a4efd1b/build.sbt#L187
(defn platform []
  (condp str/starts-with? (System/getProperty "os.name")
    "Windows" :win
    "Mac" :mac
    "Linux" :linux
    :unknown))

; https://codeberg.org/dirs/directories-jvm#basedirectories
; https://forum.atuin.sh/t/xdg-state-home-for-the-location-of-history-data/67/2
(defn platform-state-dir []
  (case (platform)
    :win (or (env "LocalAppData")
             (str (home) "\\AppData\\Local"))
    ; https://stackoverflow.com/a/14108036/7669110
    ; NOTE: wrong when running sandboxed :(
    :mac (str (home) "/Library/Application Support")
    (:linux :unknown) (fs/xdg-state-home)))

(defn state-dir [] (fs/path (platform-state-dir) "flower"))
