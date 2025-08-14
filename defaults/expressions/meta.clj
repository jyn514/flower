(require '[flower.reflect :as reflect])

(defn render
  ([source] (render source {}))
  ([source locals]
    (reflect/render-file source "<inline>" locals)))

(defn template [relative-path]
  (when-not relative-path
    (throw (AssertionError. "did not get a template name")))
  (let [bs (reflect/read-file (str "templates/" relative-path))]
    (String. ^bytes bs)))

(defn embed
  "Given a template and its local variables, render that template."
  [template-name locals]
  (let [content (template template-name)
        path (str "templates/" template-name)
        data (reflect/render-file content path locals)]
    (:content data)))

(defn include
  "Render an external template or page to a string"
  [filename] (embed filename {}))
