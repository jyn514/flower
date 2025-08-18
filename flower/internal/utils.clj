; NOTE: used from native.clj, so can't depend on anything

(ns flower.internal.utils
  (:require [babashka.process :as ps]
            [babashka.fs :as fs]
            [clojure.set :refer [union]]
            [clojure.string :as str]))

(def ^:dynamic *site* ".")

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
  (apply eprintln "flower: warning:" msg))
(defn error [& msg]
  (apply eprintln "flower: error:" msg))
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
           (error (fmt "failed to run ${opts}: exit code") (:exit (ex-data e)))
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
  (-> s str
      (str/replace "\n" "$n")
      (str/replace " " "$ ")
      (str/replace ":" "$:")
      (str/replace "$" "$$")))

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
    (:linux :unknown) (or (env "XDG_STATE_HOME")
                          (str (home) "/.local/state"))))

(defn state-dir [] (fs/path (platform-state-dir) "flower"))
