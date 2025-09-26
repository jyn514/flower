(ns transformers.standalone.split-sass-dependencies
  (:require
   [flower.reflect :as reflect]
   [flower.fs :as fs]))

(defn transform
  [{deps :sources, {:keys [source-file]} :variables}]
  (let [out-dir "public"
        relative-deps (map #(fs/relativize "." (str out-dir "/" %)) deps)]
    (reflect/gen-depfile source-file relative-deps)))
