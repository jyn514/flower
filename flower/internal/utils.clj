; NOTE: used from native.clj, so can't depend on anything

(ns flower.internal.utils
  (:require [babashka.process :as ps]
            [clojure.set :refer [union]]
            [clojure.string :as str]))

(defmacro reexport [& syms]
  (let [defs (for [sym syms]
               `(intern *ns* '~sym ~sym))]
    `(do ~@defs)))

(defn eprintln [& msg]
  (binding [*out* *err*]
    (apply println msg)))
(defn eprn [& msg]
  (binding [*out* *err*]
    (apply prn msg)))
(defn inspect [x] (eprn x) x)

(defn- err-msg [& msg]
  (with-out-str
    (apply println "flower: error:" msg)))
(defn error [& msg]
    (eprintln (apply err-msg msg)))
(defn fatal [& msg]
  (throw (ex-info (apply err-msg msg) {:flower/exit true})))
(defn warn [& msg]
  (apply eprintln "flower: warning:" msg))

(def ^:dynamic *site* ".")

(defn run [opts & rest]
  (let [[opts rest] (if (map? opts)
                      [(assoc opts :dir *site*) rest]
                      [{:dir *site*} (into opts rest)])]
    (if (sequential? rest)
      (apply ps/shell opts rest)
      (ps/shell opts rest))))

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

