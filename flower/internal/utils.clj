; NOTE: used from native.clj, so can't depend on anything

(ns flower.internal.utils
  (:require [babashka.process :as ps]
            [clojure.set :refer [union]]
            [clojure.string :as str]))

(defmacro reexport [& syms]
  (let [defs (for [sym syms]
               `(intern *ns* '~sym ~sym))]
    `(do ~@defs)))

(defmacro fmt [^String string]
  (let [-re #"\$\{(.*?)\}"
        fstr (clojure.string/replace string -re "%s")
        fargs (map #(read-string (second %)) (re-seq -re string))]
    `(format ~fstr ~@fargs)))

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

(def ^:dynamic *site* ".")

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

(defn parse-ninja [args]
  (let [out (:out (run {:out :string} args))]
    ; handle empty string
    (if (seq out)
      (str/split out #"\n")
      [])))

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
(defn indexed
  "Returns a lazy sequence of [index, item] pairs, where items come
  from 's' and indexes count up from zero.

  (indexed '(a b c d))  =>  ([0 a] [1 b] [2 c] [3 d])"
  [s]
  (map vector (iterate inc 0) s))
