; TODO: expose this as flower.defaults.build
(require
  'expressions.ninja
  '[expressions.constants :refer [use-jar rebuild-flower]]
; TODO: remove everything here but the path functions,
; make read/write access go through flower.reflect/glob-files
  '(babashka [fs :as fs])
  '[clojure.data.json :as json]
  '(clojure [string :as str]))
(use 'flower.utils)

(defn / [x & more]
  (apply fs/path x more))

(defn replace-ext [path ext]
  (-> path fs/path fs/strip-ext (str "." ext)))
(defn add-ext [path ext]
  (-> path fs/path (str "." ext)))

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
(defn flow [cmd] (fmt "${flower_cli} ${cmd} < $in > $out"))

(def expressions  (fs/glob "expressions" "**.clj"))
(def transformers (fs/glob "transformers" "**"))
(def all-pages (fs/glob "pages" "**"))

; TODO: allow pages to have a `--- include: file.ext ---` metadata
; actually wait no, emit a `depfile` instead
; TODO: allow configuring :url
(defn build-page
  ([rule page] (build-page rule page []))
  ([rule page implicits]
   (let [relative-page (remove-parent page)
         json_frontmatter (/ builddir (add-ext relative-page "json"))
         rendered (/ builddir (replace-ext relative-page (str "rendered." (fs/extension relative-page))))
         rules [{:rule "frontmatter"
                 :inputs (str page)
                 :outputs json_frontmatter
                 :implicit ff}
                {:rule rule
                 :inputs json_frontmatter
                 :outputs rendered
                 :depfile (add-ext json_frontmatter "d")
                 :implicit implicits}]]
     {:rules rules :out rendered})))

(defn markdown-page [page]
  (let [html (/ builddir (replace-ext (remove-parent page) "html"))]
    {:rules [{:rule "markdown"
              :inputs page
              :outputs html
              :implicit ff}]
     :out html}))

(defn transform-page [rendered]
  (let [depfile (add-ext rendered "d")
        ; a.rendered.html -> a.html
        [base ext] (fs/split-ext rendered)
        filename (if (= "rendered" (fs/extension base))
                   (replace-ext base ext)
                   base)
        final (/ public (remove-parent filename))]
    ; NOTE: if you have a custom build command you have to add a :build yourself
    {:rules [{:rule "transform"
               :inputs rendered
               :outputs final
               :implicit transformers
               :depfile depfile}]
      :out nil}))

(defmulti dispatch-file fs/extension)
(defmethod dispatch-file "md" [f] (markdown-page f))
(defmethod dispatch-file :default [f] (transform-page f))
(defn chain-commands [in out]
  (let [{new-rules :rules
         new-path :out } (dispatch-file in)
        all-rules (concat out new-rules)]
    (if (nil? new-path)
      all-rules
      (recur new-path all-rules))))

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
  (let [relative-path (remove-parent path)
        out (/ public (replace-ext relative-path "css"))
        source-map (add-ext out "map")
        depfile (/ builddir (add-ext relative-path "d"))]
    {:rule "sass"
    :inputs (str path)
    :outputs out
    :source-map source-map
    :depfile depfile}))

(def sass-builds (map sass->build sass-files))
(def sass-outputs (map :outputs sass-builds))

(def page-builds
  (let [page-frontmatter (:pages flower.reflect/*frontmatter*)
        [indexes pages] (split-all (fn [[_ meta]] (:index meta)) page-frontmatter)
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
  ; NOTE: we can't put lists here, ninja interprets them as literal strings
  ; https://codeberg.org/jyn514/flower/issues/16
  {:variables {:builddir builddir}
   :phony [{:name "flower" :depends ff}]
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
     :command (fmt "${flower_cli} render-page < $in | ${flower_cli} split-dependencies $in.d $out > $out")
     :description "render page $in using clojure"}
    {:name "index"
     :command (fmt "${flower_cli} render-index < $in | ${flower_cli} split-dependencies $in.d $out > $out")
     :description "render index page $in using clojure"}
    {:name "frontmatter"
     :command (fmt "${flower_cli} jq -R --filename $in '{filename: $$filename, content: .}' < $in | ${flower_cli} split-frontmatter > $out")}
    {:name "sass"
     :command (fmt "sass --quiet $in $out; ${flower_cli} split-sass-dependencies $out <$source-map >$depfile")
     :description "compile Sass file $in to CSS"}
    {:name "markdown"
     ; TODO: use flower builtins
     :command (flow "render-markdown")
     :description "render markdown -> HTML: $in -> $out"}]
   :builds
   [{:rule "ninja-meta"
     :restat true
     :generator true
     :outputs "build.ninja"
     ; TODO: maybe we need to nest pages in builddir so they don't conflict?
     :depfile (/ builddir "build.clj.d")
     ; TODO: remove all-pages once we get rid of render-index
     :inputs (concat all-pages ["build.clj"] ff
                     (mapcat all-dirs ["pages" "templates" "expressions" "sass"]))}
    (when rebuild-flower
      {:rule "flower-meta"
        :outputs ff
        :inputs (concat (fs/glob "../flower" "**") defaults ["../native.clj" "../flower" "../deps.edn"])})
    (when rebuild-flower
       {:rule "flower-defaults"
        :outputs "../defaults/build.ninja"
        :inputs "../defaults/build.clj"})
    {:rule "tmpdir"
     :outputs builddir}]})

(def trans-map {"clj" (str flower_cli " transform")})

; TODO: unix pipelines are so jank lol. run this as a single `flower transform` command so we can do proper error handling.
(def transform
        ; TODO: shell escaping
  (let [files (->> transformers (map str) (str/join " "))
        ; well this kinda sucks. $in is quoted but $depfile is not, so we can't use it.
        ; instead we assume it's always relative to $in.
        cmd "flower transform < $in $in.d $out $transform-map $transformers"]
    {:variables {:transformers files
                 :transform-map (-> trans-map json/write-str escape-shell)}
     :rules [{:name "transform"
              :command cmd
              :description "run all transformers on $in"}]}))

(expressions.ninja/generate
  (merge-deep transform page-builds
              (update base :builds #(concat % ))))
