(ns transformers.standalone.generate-redirect
  (:require
   [expressions.meta :refer [embed]]
   [expressions.utils :refer [inspect]]))

(defn transform [target]
  (embed "redirect.html" {'target target}))
