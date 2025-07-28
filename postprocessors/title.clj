(use 'flower.select 'hiccup2.core 'flower.utils)
(defn transform [{page :content}]
  (println page)
  (let [title (:content (select page "h1"))]
    (append (select page "head")
            (html [:title title]))
    page))
