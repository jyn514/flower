(ns expressions.meta
  (:require
   [clj-commons.digest :as digest]
   [expressions.utils :refer [as-map concat-bytes]]
   [flower.fs :as fs]
   [flower.reflect :as reflect :refer [preprocess-sunflower]]))

(defn locals
  "Return a map of all local variables passed to this clojure context.
  The values of the map are the value of the variable, not a clojure Var.
  A 'clojure context' is reset on each call to `flower.reflect/preprocess-sunflower`."
  []
  (update-vals (ns-publics 'flower.locals) #(if (var? %) (deref %) %)))

(def all-preprocessors
  {"sunflower" preprocess-sunflower})

(defn preprocess-file [opts]
  (reflect/preprocess-file (assoc opts :all-preprocessors all-preprocessors)))

(defn render
  "Render an in-memory string as a sunflower template."
  ([source] (render source {}))
  ([source locals]
    (preprocess-file {:content source
                      :frontmatter {:flower/source-file "<inline>"}
                      :locals locals})))

(defn template
  "Load the contents of a template file from disk.
  `path` is assumed to be relative to the templates/ directory."
  [path]
  (when-not path
    (throw (AssertionError. "did not get a template name")))
  (slurp (if (fs/absolute? path) path
                (str "templates/" path))))

(defn embed
  "Given a template and its local variables, render that template."
  ([template-name locals] (embed template-name locals {}))
  ([template-name locals opts]
   (let [[locals opts] (if (string? opts)
                         [(assoc locals 'content opts) {}]
                         [locals opts])
         content (template template-name)
         path (if (fs/absolute? template-name) template-name
                (str "templates/" template-name))
         frontmatter (assoc opts :flower/source-file path)
         data (preprocess-file (as-map content locals frontmatter))]
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
