(ns flower.internal-utils
  (:require [babashka.process :as ps]
            [clojure.string :as str]))

(defn parse-ninja [args]
  (let [out (:out (ps/shell {:out :string} args))]
    ; handle empty string
    (if (seq out)
      (str/split out #"\n")
      [])))
