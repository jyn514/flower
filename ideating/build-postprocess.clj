(ns build-transform)

; give an error if they miss one
(def transform-order
  ; (concat ["embed.clj"] (glob "transformers" "**")))
  ["embed.clj"
   "foo.clj"
   "bar.clj"])
