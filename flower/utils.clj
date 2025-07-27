(ns flower.utils)

; helpers

(defmacro fmt [^String string]
  (let [-re #"\$\{(.*?)\}"
        fstr (clojure.string/replace string -re "%s")
        fargs (map #(read-string (second %)) (re-seq -re string))]
    `(format ~fstr ~@fargs)))

(defn error [msg] (binding [*out* *err*]
                    (println (str "flower: error: " msg))))

; https://groups.google.com/g/clojure/c/UdFLYjLvNRs/m/8fd9fvNur6cJ
(defn merge-deep [& maps]
  (if (every? map? maps)
    (apply merge-with merge-deep maps)
    (last maps)))

(defn inspect [x] (println x) x)

