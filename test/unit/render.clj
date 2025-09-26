(ns test.unit.render
  (:require
   [expectations.clojure.test :refer [defexpect expect]]
   [flower.eval :as eval]))

(defn render [s] (eval/preprocess-sunflower s "<render-test>"))
(defn expect-render [& args]
  (doseq [[sunflower expanded] (partition 2 args)]
    (expect expanded (render sunflower) sunflower)))

(defexpect edge-cases
  (expect-render
    "◊(when false)"  ""
    "◊◊;"  "◊;"
    "◊(if false)«a»«b»" "b"))
