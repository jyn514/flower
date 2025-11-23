(ns expressions.meta
  (:require
   [clj-commons.digest :as digest]
   [transformers.preprocess :refer [preprocess-file]]
   [flower.fs :as fs]))

(defn locals
  "Return a list of all local variables passed to this clojure context.
  A 'clojure context' is reset on each call to `flower.reflect/preprocess-sunflower`."
  []
  (ns-publics 'flower.locals))

(defn render
  ([source] (render source {}))
  ([source locals]
    (preprocess-file source "<inline>" locals)))

(defn template [relative-path]
  (when-not relative-path
    (throw (AssertionError. "did not get a template name")))
  (slurp (str "templates/" relative-path)))

(defn embed
  "Given a template and its local variables, render that template."
  ([template-name locals] (embed template-name locals {}))
  ([template-name locals opts]
   (let [content (template template-name)
         path (str "templates/" template-name)
         data (preprocess-file content path locals opts)]
     data)))

(defn include
  "Render an external template or page to a string"
  ([filename] (embed filename {}))
  ([filename opts] (embed filename {} opts)))

(defn hash
  "Calculate the SHA256 hash of a file."
  [filename]
  (-> filename fs/read-all-bytes digest/sha-256))
