; (ns transformers.markdown)
(require '[flower.utils :refer [md->html]])
(defn transform [{page :content meta :frontmatter :as args}]
  (if (= "md" (:flower/filetype meta))
    (let [new-meta (assoc meta :flower/filetype "html")]
      (assoc args
             :content (md->html page)
             :frontmatter new-meta))
    page))
