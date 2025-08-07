; TODO: expose this as flower.defaults.build
(require 'flower.build
         '(babashka [fs :as fs])
         '(clojure [string :as str]))
(use 'flower.utils)

; TODO: configuration mechanism using ninja phony targets
; (def use-jar (boolean (System/getenv "FLOWER_SKIP_GRAAL")))
(def use-jar false)
(def rebuild-flower false)

(defn / [x & more]
  (apply fs/path x more))

(defn ext [path ext]
  (-> path fs/path fs/file-name fs/strip-ext (str "." ext)))

(def public "public")
(def builddir ".build")
(def templates "templates")
(def f "flower")
(def ff
  (if-not rebuild-flower []
    [(if use-jar "../target/flower.jar" "../target/flower")]))
(def flower_cli
  (if rebuild-flower "../target/flower" "flower"))
(defn flow [cmd] (fmt "${flower_cli} ${cmd} <$in >$out"))

(def expressions (fs/glob "expressions" "**.clj"))

; TODO: allow pages to have a `--- include: file.ext ---` metadata
; actually wait no, emit a `depfile` instead
(defn build-page
  ([rule page] (build-page rule page []))
  ([rule page implicits]
   (let [template (-> (get flower.build/all-frontmatter page) :template (or "default.html"))
         template_path (/ templates template)
         json_frontmatter (/ builddir (ext page "md.json"))
         processed_markdown (/ builddir (ext page "md.rendered.json"))
         ; can be different than embedded_html if e.g. the template ends in .md
         embedded_markdown (/ builddir (ext page (str (fs/extension template) ".embed")))
         ; TODO: needs a different name, this setup causes the embed to be copied into the final dir
         embedded_html (/ builddir (ext page "html.embed"))
         depfile (/ builddir (ext page "html.embed.d"))
         final_html (/ public (ext page "html"))
         rules [{:rule "frontmatter"
                 :inputs (str page)
                 :outputs json_frontmatter
                 :implicit ff}
                {:rule rule
                 :inputs json_frontmatter
                 :outputs processed_markdown
                 :implicit (concat implicits expressions)}
                {:rule "template"
                 :inputs processed_markdown
                 :outputs embedded_markdown
                 :implicit (concat (conj ff template_path) expressions)
                 :template template_path}
                ; TODO: wrong, should run on all html files, not just pages
                ; maybe we can make transform a dispatch-file rule, output `.processed`,
                ; and add a dispatch-file rule for .processed?
                ; wait no dispatch-file only runs on pages
                ; ok never mind, if you have a custom build command you have to add a :build yourself
                {:rule "transform"
                 :inputs embedded_html
                 :outputs final_html
                 :implicit (concat (fs/glob "transformers" "*") expressions)
                 :depfile depfile
                 :html embedded_html}]]
     ; TODO: this will break for md->html generation because it will also copy .embed to public/
     (if (= embedded_markdown embedded_html) {:rules rules}  {:rules rules :out embedded_markdown})
     ) ))

(defn markdown-page [page]
  (let [html (/ builddir (ext page "html"))]
    {:rules [{:rule "markdown"
              :inputs page
              :outputs html
              :implicit ff}]
     :out html}))

(defn link-page [page]
  (let [final (/ public (fs/file-name page))]
    {:rules [{:rule "link"
              :inputs page
              :outputs final}]}))

(defmulti dispatch-file fs/extension)
(defmethod dispatch-file "md" [f] (markdown-page f))
; (defmethod dispatch-file "html" [f] (link-page f))
(defmethod dispatch-file :default [f] (link-page f))
(defn chain-commands [in out]
  (let [{new-rules :rules
         new-path :out } (dispatch-file in)
        all-rules (concat out new-rules)]
    (if (nil? new-path)
      all-rules
      (recur new-path all-rules))))

(def all-pages (fs/glob "pages" "**.md"))

(defn chain-page [pages build-func]
  (mapcat
    #(let [{:keys [rules out]} (build-func %)]
       (if out (chain-commands out rules) rules))
    pages))
(def page-builds (chain-page all-pages #(build-page "page" %)))

(defn index [{frontmatter :all-frontmatter}]
  ; TODO: this is wrong, index pages should be pages, not templates
  (let [index-meta (filter #(get % "index") (:templates frontmatter))
        index-paths (map :file index-meta)
        index-builds (chain-page index-paths #(build-page "index" % "build.ninja"))]
    {:builds index-builds}))

(def base
  {:variables {:builddir builddir}
   :rules
   [{:name "ninja-meta"
     :command (fmt "${flower_cli} configure")
     :description "rebuild build.ninja itself"}
    {:name "flower-meta"
     :command (if use-jar "cd .. && clojure -T:build uberjar" "cd .. && clojure -T:build native-dev")
     :description "rebuild flower itself"}
    {:name "tmpdir"
     :command (str "mkdir -p " builddir)
     :description "create build dir"}
    {:name "link"
     :command "ln -f $in $out"
     :description "link $in into build dir"}
    {:name "page"
     :command (flow "render-page")
     :description "render page $in using clojure"}
    {:name "index"
     :command (flow "render-index")
     :description "render index page $in using clojure"}
    {:name "template"
     ; NOTE: this means that all templates must depend on all other templates
     ; TODO: `flow` should take arbitrary number of args
     :command (fmt "${flower_cli} embed-template $template < $in > $out")
     :description "embed $in into $template using clojure"}
    {:name "frontmatter"
     ; TODO: maybe add an `--arg` equivalent idk
     :command (fmt "${flower_cli} jq -R \"{filename: \\\"$in\\\", content: .}\" < $in | ${flower_cli} split-frontmatter > $out")}
    {:name "markdown"
     ; TODO: use flower builtins
     :command "pulldown-cmark -TFSULG $in -> $out"
     :description "render markdown -> HTML: $in -> $out"}]
   :builds
   ; TODO: this should be in flower/build.clj so it can do proper dependency tracking
   [{:rule "ninja-meta"
     :restat true
     :outputs "build.ninja"
     :inputs (concat all-pages (fs/glob templates "**") ["build.clj"] ff)}
    (if rebuild-flower
      {:rule "flower-meta"
      :outputs ff
      :inputs (conj (fs/glob "../flower" "**") "../flower")})
    {:rule "tmpdir"
     :outputs builddir}]})

; TODO: needs to register `depfile`
; see /home/jyn/src/example/example-edbf02f84e934656.d for example
(defn transform [{runners :all-transformers}]
  (let [pps (fs/glob "transformers" "*")
        cmds (map #(str (get runners (fs/extension %)) " " %) pps)
        pipe (str/join " | " cmds)
        cmd (fmt "< $in ${pipe} | ${flower_cli} split-dependencies $depfile $out | ${flower_cli} jq .content -r > $out")]
  {:rules [{:name "transform"
            :command cmd
            :description "run all transformers on $in"}]}))

(flower.build/register-transformer-runners
  {"clj" (str flower_cli " transform")})
(flower.build/generate
  (update-in base [:builds] #(concat % page-builds))
  index transform)
