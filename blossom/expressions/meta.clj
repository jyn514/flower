(ns expressions.meta
  (:require
   [clj-commons.digest :as digest]
   [flower.fs :as fs]
   [flower.reflect :as reflect]))

(defn render
  ([source] (render source {}))
  ([source locals]
    (reflect/render-file source "<inline>" locals)))

(defn template [relative-path]
  (when-not relative-path
    (throw (AssertionError. "did not get a template name")))
  (slurp (str "templates/" relative-path)))

(defn embed
  "Given a template and its local variables, render that template."
  [template-name locals]
  (let [content (template template-name)
        path (str "templates/" template-name)
        data (reflect/render-file content path locals)]
    data))

(defn include
  "Render an external template or page to a string"
  [filename] (embed filename {}))

(defn hash
  "Calculate the SHA256 hash of a file."
  [filename]
  (-> filename fs/read-all-bytes digest/sha-256))
