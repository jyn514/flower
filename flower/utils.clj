(ns flower.utils
  (:require [hiccup2.core :as hiccup]
            [nextjournal.markdown :as md]))

; helpers
; NOTE: these helpers are exposed to all interpreted code,
; so they must not interact with the filesystem.

(defmacro fmt [^String string]
  (let [-re #"\$\{(.*?)\}"
        fstr (clojure.string/replace string -re "%s")
        fargs (map #(read-string (second %)) (re-seq -re string))]
    `(format ~fstr ~@fargs)))

(defn error [& msg] (binding [*out* *err*]
                    (println (apply str "flower: error: " msg))
                    (System/exit 1)))

; https://groups.google.com/g/clojure/c/UdFLYjLvNRs/m/8fd9fvNur6cJ
(defn merge-deep [& maps]
  (if (every? map? maps)
    (apply merge-with merge-deep maps)
    (last maps)))

(defn inspect [x] (binding [*out* *err*] (println x) x))

(defn markdown [md]
  ; https://github.com/nextjournal/markdown?tab=readme-ov-file#html-blocks-and-html-inlines
  (let [renderers (assoc md/default-hiccup-renderers
                         :html-inline (comp hiccup/raw md/node->text)
                         :html-block (comp hiccup/raw md/node->text))]
  (->> md (md/->hiccup renderers) hiccup/html str)))

