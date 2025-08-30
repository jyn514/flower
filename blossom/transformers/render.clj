; (ns transformers.render
  (require '[flower.reflect :as reflect])
(defn transform [{page :content meta :frontmatter}]
  (reflect/render-file page (:flower/source-file meta)))
