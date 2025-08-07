(use 'hiccup2.core 'flower.utils 'flower.reflect 'flower.expressions.html)

(def ^:dynamic root)

(defn transform [{page :content}]
  ; replace-with! modifies in place, so we need to hold on to the root node
  (binding [root (document page)]
    (loop [tags (select root "flower-embed")]
      (when (seq tags)
        (doseq [tag tags]
          (let [embedded (html tag)
                attrs (attrs tag)
                template-name (get attrs "template")
                template (flower.reflect/template template-name)
                locals {(symbol (get attrs "name")) embedded}
                rendered (flower.reflect/render template template-name locals)]
            (if (is-root rendered)
              (set! root rendered)
              (replace-with! tag rendered))))
        (recur (select root "flower-embed"))))
      root))
