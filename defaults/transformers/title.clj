(use 'expressions.html 'hiccup2.core 'flower.utils)
(defn transform [{page :content}]
  (let [title (:content (select page "h1"))]
    (append! (select page "head")
            (innerHtml [:title title]))
    page))
