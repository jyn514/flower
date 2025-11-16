(ns transformers.standalone.generate-tags 
  (:require
   [clojure.edn :as edn]
   [expressions.meta :refer [embed]]))

(defn transform [raw]
  (let [readers {'ordered/map #(into (sorted-map) %)}
        [tag posts] (edn/read-string {:readers readers} raw)]
    (embed "tags.html" {'frontmatter posts 'tag tag})))
