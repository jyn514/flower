(ns build-postprocess)

; give an error if they miss one
(def postprocess-order
  ; (concat ["embed.clj"] (glob "postprocessors" "**")))
  ["embed.clj"
   "foo.clj"
   "bar.clj"])
