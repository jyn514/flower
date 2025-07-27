(require 'flower.build)
(use 'flower.utils)

(defn / [x & more]
  (apply fs/path x more))

(defn ext [path ext]
  (-> path fs/path fs/file-name fs/strip-ext (str "." ext)))

(def builddir ".build")
(def f "flower")
(def ff
  "flower files"
  (conj
    (map #(/ f (ext % "clj"))
         ["parse" "select" "utils" "build"])
    "deps.edn"))
(def flower_cli "clj -M --main flower.core")
(defn flow [cmd] (fmt "${flower_cli} ${cmd} <$in >$out"))

(def all-pages (fs/glob "pages" "**.md"))
(defn build-page [page]
  ; (let [template (-> page slurp build/split-frontmatter :frontmatter :template)
  (let [template (-> (get flower.build/all-frontmatter page) :template (or "default.html"))
        json_frontmatter (/ builddir (ext page "md.json"))
        processed_markdown (/ builddir (fs/file-name page))
        embedded_markdown (/ builddir (ext page "embed.md"))
        html (/ builddir (ext page "html"))]
    [{:rule "frontmatter"
      :inputs (str page)
      :outputs json_frontmatter
      :implicit ff}
     {:rule "page"
      :inputs json_frontmatter
      :outputs processed_markdown}
     {:rule "template"
      :inputs processed_markdown
      :outputs embedded_markdown
      :template template}
     {:rule "markdown"
      :inputs embedded_markdown
      :outputs html}]))
(def page-builds (mapcat build-page all-pages))

(def base
  {:variables {:builddir builddir}
   :rules
   [{:name "ninja-meta"
     :command (fmt "${flower_cli} configure")
     :description "rebuild build.ninja itself"}
    {:name "tmpdir"
     :command (str "mkdir -p " builddir)
     :description "create build dir"}
    {:name "link"
     :command "ln -f $in out"
     :description "link $in into build dir"}
    {:name "page"
     :command (flow "render-page")
     :description "render $in using clojure"}
    {:name "template"
     ; NOTE: this means that all templates must depend on all other templates
     :command (fmt "${flower_cli} embed-template $template < $in > $out")
     :description "embed $in into $template using clojure"}
    {:name "postprocessor"
     :command (flow "postprocess")
     :description "transform $html with $postprocessor using clojure"}
    {:name "frontmatter"
     :command (flow "split-frontmatter")}
    {:name "markdown"
     :command "pulldown-cmark -TFSULG $in -> $out"
     :description "render markdown -> HTML: $in -> $out"}]
   :builds
   ; TODO: this should be in flower/build.clj so it can do proper dependency tracking
   [{:rule "ninja-meta"
     :outputs "build.ninja"
     :inputs (concat all-pages ["build.clj" (/ f "build.clj") (/ f "parse.clj")])}
    {:rule "tmpdir"
     :outputs builddir}]})

(flower.build/generate
  (update-in base [:builds] #(concat % page-builds)))
