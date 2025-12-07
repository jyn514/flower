(ns expressions.meta
  (:require
   [clj-commons.digest :as digest]
   [expressions.utils :refer [concat-bytes]]
   [flower.fs :as fs]
   [transformers.preprocess :refer [preprocess-file]]))

(defn locals
  "Return a map of all local variables passed to this clojure context.
  The values of the map are the value of the variable, not a clojure Var.
  A 'clojure context' is reset on each call to `flower.reflect/preprocess-sunflower`."
  []
  (update-vals (ns-publics 'flower.locals) #(if (var? %) (deref %) %)))

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
   (let [[locals opts] (if (string? opts)
                         [(assoc locals 'content opts) {}]
                         [locals opts])
         content (template template-name)
         path (str "templates/" template-name)
         data (preprocess-file content path locals opts)]
     data)))

(defn include
  "Render an external template or page to a string"
  ([filename] (embed filename {}))
  ([filename opts] (embed filename {} opts)))

(defn hash
  "Calculate the SHA256 hash of one or more files.
   If multiple files are present, they will be concatenated in order before being hashed."
  [& paths]
  (->> paths (map fs/read-all-bytes) (apply concat-bytes) digest/sha-256))
