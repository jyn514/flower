(ns transformers.redirect
  (:use expressions.meta))
(defn transform [{page :content meta :frontmatter :as args}]
  (if-let [redirect (:redirect meta)]
    (let [bindings (assoc (update-keys args symbol)
                          'target redirect)
          content (embed "redirect.html" bindings)]
      {:content content :frontmatter (assoc meta :template nil)})
    args))

