(use 'flower.select 'hiccup2.core 'flower.utils)
(defn transform [{page :content}]
  (let [tag (select page "flower-embed")
        embedded (flower.select/html tag)
        attrs (flower.select/attrs tag)
        template (-> attrs :template flower.reflect/template)
        locals {(symbol (:name attrs)) page}
        rendered (flower.reflect/render template locals)]
    ; (append (select page "head")
    ;         (html [:title title]))
    (flower.select/replace-with! tag rendered)
    page))
