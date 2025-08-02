(ns flower.internal-utils
  (:use [flower.utils])
  (:require [babashka.process :as ps]
            [clojure.string :as str]))
(defn error [& msg]
  (let [msg (with-out-str
              (apply println "flower: error:" msg))]
    ; (throw flower.
    (throw (ex-info msg {:flower/exit true}))))
(defn warn [& msg]
  (apply eprintln "flower: warning:" msg))

(def ^:dynamic *site* ".")

(defn run [opts & rest]
    (println rest)
  (let [defaults {:dir *site}
        [opts rest]
        (cond
          (map? opts) [(merge defaults opts) rest]
          (string? opts) [defaults (if (string? (concat opts rest)])]
    (println rest)
    (apply ps/shell opts rest)))

(defn parse-ninja [args]
  (let [out (:out (run {:out :string} args))]
    ; handle empty string
    (if (seq out)
      (str/split out #"\n")
      [])))
