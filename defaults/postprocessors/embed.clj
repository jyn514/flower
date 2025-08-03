(use 'flower.select 'hiccup2.core 'flower.utils 'flower.reflect)
(defn transform [{page :content}]
  (doseq [tag (select page "flower-embed")]
    (let [embedded (flower.select/html tag)
          attrs (flower.select/attrs tag)
          template (-> attrs :template flower.reflect/template)
          locals {(symbol (:name attrs)) embedded}
          rendered (flower.reflect/render template locals)]
      (flower.select/replace-with! tag rendered)))
    page)
