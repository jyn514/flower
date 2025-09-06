(ns transformers.embed
  (:use expressions.meta))
(defn transform [{page :content meta :frontmatter :as args}]
  (let [template (:template meta "default.html")]
    (if (some? template)
      (embed template (update-keys args symbol))
      page)))
