(use 'hiccup2.core 'flower.utils 'flower.reflect 'flower.expressions.html)
(defn transform [{page :content}]
  ; replace-with! modifies in place, so we need to hold on to the root node
  (let [doc (document page)]
  (doseq [tag (select doc "flower-embed")]
    (let [embedded (html tag)
          attrs (attrs tag)
          template-name (get attrs "template")
          template (flower.reflect/template template-name)
          locals {(symbol (get attrs "name")) embedded}
          ; TODO: this includes the tag we were trying to replace
          rendered (flower.reflect/render template template-name locals)]
      (replace-with! tag rendered)))
    doc))
