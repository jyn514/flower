; TODO: expose this as flower.defaults.build
(require
  'expressions.ninja
  '(babashka [fs :as fs])
  '(clojure [string :as str]))
(use 'flower.utils)

; TODO: configuration mechanism using ninja phony targets
; (def use-jar (boolean (System/getenv "FLOWER_SKIP_GRAAL")))
(def use-jar false)
(def rebuild-flower false)

(defn / [x & more]
  (apply fs/path x more))

(defn replace-ext [path ext]
  (-> path fs/path fs/file-name fs/strip-ext (str "." ext)))
(defn add-ext [path ext]
  (-> path fs/path fs/file-name (str "." ext)))

(defn all-dirs [root]
  (let [dirs (atom (if (fs/exists? root) #{root} #{}))
        update #(swap! dirs conj %)
        visitor (fn [path _attrs] (update path) :continue)]
    (fs/walk-file-tree root {:pre-visit-dir visitor})
    (map str @dirs)))

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
; TODO: allow configuring :url
(defn build-page
  ([rule page] (build-page rule page []))
  ([rule page implicits]
   (let [json_frontmatter (/ builddir (add-ext page "json"))
         ; can be different than processed_html if e.g. the template ends in .md
         processed_markdown (/ builddir (replace-ext page (str "rendered." (fs/extension page))))
         processed_html (/ builddir (replace-ext page "rendered.html"))
         depfile (/ builddir (replace-ext page "rendered.html.d"))
         final_html (/ public (replace-ext page "html"))
         rules [{:rule "frontmatter"
                 :inputs (str page)
                 :outputs json_frontmatter
                 :implicit ff}
                {:rule rule
                 :inputs json_frontmatter
                 :outputs processed_markdown
                 ; TODO: check if we can remove unconditional dependency on expressions/ now that load-fn does dep tracking
                 :implicit (concat implicits expressions)}
                ; TODO: wrong, should run on all html files, not just pages
                ; maybe we can make transform a dispatch-file rule, output `.processed`,
                ; and add a dispatch-file rule for .processed?
                ; wait no dispatch-file only runs on pages
                ; ok never mind, if you have a custom build command you have to add a :build yourself
                {:rule "transform"
                 :inputs processed_html
                 :outputs final_html
                 :implicit (concat (fs/glob "transformers" "*") expressions)
                 :depfile depfile}]]
     ; TODO: this will break for md->html generation because it will also copy .embed to public/
     (if (= processed_markdown processed_html)
       {:rules rules}
       {:rules rules :out processed_markdown}))))

(defn markdown-page [page]
  (let [html (/ builddir (replace-ext page "html"))]
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
(defmethod dispatch-file :default [f] (link-page f))
(defn chain-commands [in out]
  (let [{new-rules :rules
         new-path :out } (dispatch-file in)
        all-rules (concat out new-rules)]
    (if (nil? new-path)
      all-rules
      (recur new-path all-rules))))

(def all-pages (fs/glob "pages" "**"))

(defn chain-page [pages build-func]
  (mapcat
    #(let [{:keys [rules out]} (build-func %)]
       (if out (chain-commands out rules) rules))
    pages))

; why doesn't split-with do this by default ;-;
(defn split-all [f seq]
  [(filter f seq) (filter #(not (f %)) seq)])

(def sass-files
  ; excludes /_*.sass
  (filter #(-> % fs/file-name first (= \_) not)
          (fs/glob "sass" "**.{scss,sass}")))

(defn sass->build [path]
  (let [out (/ public (replace-ext path "css"))
        source-map (/ public (add-ext out "map"))
        depfile (/ builddir (add-ext path "d"))]
    {:rule "sass"
    :inputs (str path)
    :outputs out
    :source-map source-map
    :depfile depfile}))

(def sass-builds (map sass->build sass-files))
(def sass-outputs (map :outputs sass-builds))

(def pages
  (let [page-frontmatter (:pages flower.reflect/*frontmatter*)
        [indexes pages] (split-all (fn [[_ meta]] (get meta "index")) page-frontmatter)
        index-paths (map first indexes)
        page-paths (map first pages)
        ; sass here is a hack until i implement hash-inputs
        index-builds (chain-page index-paths #(build-page "index" % (concat page-paths sass-outputs ["build.ninja"])))
        page-builds (chain-page page-paths #(build-page "page" % sass-outputs))]
    {:builds (concat index-builds page-builds)}))

(def defaults
  ; MANIFEST.txt gets rebuilt when we rebuild flower.
  ; avoid it always showing up as dirty.
  (remove #{(fs/path "../defaults/MANIFEST.txt")}
          (fs/glob "../defaults" "**")))

(def base
  {:variables {:builddir builddir}
   :rules
   [{:name "ninja-meta"
     :command (fmt "${flower_cli} configure")
     :description "rebuild build.ninja itself"}
    {:name "flower-meta"
     :command (if use-jar "cd .. && clojure -T:build uberjar" "cd .. && clojure -T:build native-dev")
     :description "rebuild flower itself"}
    {:name "flower-defaults"
     :command (fmt "cd ../defaults && ${flower_cli} configure")
     :description "rebuild default build.ninja"}
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
    {:name "sass"
     :command (fmt "sass --quiet $in $out; ${flower_cli} split-sass-dependencies $out <$source-map >$depfile")
     :description "compile Sass file $in to CSS"}
    {:name "markdown"
     ; TODO: use flower builtins
     :command (flow "render-markdown")
     :description "render markdown -> HTML: $in -> $out"}]
   :builds
   ; TODO: this should be in flower/build.clj so it can do proper dependency tracking
   [{:rule "ninja-meta"
     :restat true
     :outputs "build.ninja"
     :inputs (concat all-pages (fs/glob templates "**") ["build.clj"] ff
                     (mapcat all-dirs ["pages" "templates" "expressions" "sass"]))}
    (when rebuild-flower
      {:rule "flower-meta"
        :outputs ff
        :inputs (concat (fs/glob "../flower" "**") defaults ["../flower" "../deps.edn"])})
    (when rebuild-flower
       {:rule "flower-defaults"
        :outputs "../defaults/build.ninja"
        :inputs "../defaults/build.clj"})
    {:rule "tmpdir"
     :outputs builddir}]})

(def trans-map {"clj" (str flower_cli " transform")})

; TODO: unix pipelines are so jank lol. run this as a single `flower transform` command so we can do proper error handling.
(def transform
  (let [pps (fs/glob "transformers" "*")
        cmds (map #(str (get trans-map (fs/extension %)) " " %) pps)
        pipe (str/join " | " cmds)
        cmd (fmt "< $in ${pipe} | ${flower_cli} split-dependencies $depfile $out | ${flower_cli} jq .content -r > $out")]
    {:rules [{:name "transform"
              :command cmd
              :description "run all transformers on $in"}]}))

(expressions.ninja/generate
  (merge-deep transform pages (update base :builds #(concat % sass-builds))))
