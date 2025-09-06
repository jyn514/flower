(ns flower.utils
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

(defn split-once [s delim] (str/split s delim 2))

(defn update-meta
  "Given a value x, run `f` on its metadata and apply the result as x's metadata"
  [f x]
  (->> x meta f (with-meta x)))

(defn pluralize [x desc]
  (if (= 1 (count x)) desc
    (str desc "s")))

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

(defn msg [& msg]
  (apply eprintln (fmt "flower${*cmd*}:") msg))
(defn info [& msgs] (apply msg "info:" msgs))
(defn warn [& msgs] (apply msg "warning:" msgs))
(defn error [& msgs] (apply msg "error:" msgs))

(defn fatal [opts & msg]
  (let [[info msg] (if (map? opts)
                     [(merge {:flower/expected true} opts) msg]
                     [{:flower/expected true} (concat [opts] msg)])
        formatted (apply str (interpose " " msg))]
    (throw (ex-info formatted info))))

(defn run [opts & rest]
  (let [[opts rest] (if (map? opts)
                      [(assoc opts :dir *site*) rest]
                      [{:dir *site*} (into opts rest)])
        opts (merge-deep {:extra-env {"NINJA_STATUS" "[%f/%t (%r running)] "}}
                         opts)]
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

(defn strip-suffix [^String s ^String suffix]
  (if-not (.endsWith s suffix) s
    (.substring s 0 (- (count s) (count suffix)))))

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
      (fatal {:flower/parse true} (render-parse-error err description))
      ev)))

(defn parse-ninja [args]
  (let [out (:out (run {:out :string} args))]
    ; handle empty string
    (if (seq out)
      (str/split out #"\n")
      [])))

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

; TODO: i think this won't return the initial `ex` :(
(defn ex-causes [ex]
  (iteration ex-cause :initk ex))

(def env System/getenv)

(defn home [] (System/getProperty "user.home"))

; https://github.com/NetLogo/NetLogo/blob/de24f273963a18cf42c257301f2921b90a4efd1b/build.sbt#L187
(defn platform []
  (condp #(str/starts-with? %2 %1) (System/getProperty "os.name")
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
    ; https://becca.ooo/blog/macos-dotfiles/
    (:mac :linux :unknown) (fs/xdg-state-home)))

(defn state-dir [] (fs/path (platform-state-dir) "flower"))

(defn graal? []
  (some? (System/getProperty "org.graalvm.home")))
