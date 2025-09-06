(ns transformers.title
  (:use expressions.html hiccup2.core))
(defn transform [{page :content}]
  (let [title (:content (select page "h1"))]
    (append! (select page "head")
            (html [:title title]))
    page))
