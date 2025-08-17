(use 'flower.utils 'expressions.meta)
(defn transform [{page :content meta :frontmatter :as args}]
  (let [template (:template meta "default.html")]
    (println template)
    (if (some? template)
      (embed template (update-keys args symbol))
      page)))
