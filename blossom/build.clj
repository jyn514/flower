; TODO: expose this as flower.defaults.build
(import 'java.util.List)
(require
  '[flower.reflect :as reflect]
  'expressions.ninja
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
(defn prepend-ext [path new-ext]
  (let [[base old-ext] (-> path fs/path fs/split-ext)]
    (str/join "." [base new-ext old-ext])))

(defn all-dirs [root]
  (let [dirs (atom (if (fs/exists? root) #{root} #{}))
        update #(swap! dirs conj %)
        visitor (fn [path _attrs] (update path) :continue)]
    (fs/walk-file-tree root {:pre-visit-dir visitor})
    (map str @dirs)))

(def settings (:settings reflect/*metadata*))
(def rebuild-flower (get settings "rebuild-flower"))
(def use-jar (= "jar" rebuild-flower))

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
(def all-pages (remove fs/directory? (fs/glob "pages" "**")))
(def joined-frontmatter (/ builddir "all-frontmatter.json"))

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

(def sass-builds {:builds (map sass->build sass-files)})
(def sass-outputs (map :outputs (:builds sass-builds)))

(defn frontmatter-path [page]
  (/ builddir (add-ext (remove-parent page) "json")))

(def all-frontmatter
  {:rules [{:name "join-frontmatter"
            :command (fmt "${flower_cli} join-frontmatter $out $in")
            :restat true
            :description "join all page frontmatter into a cache"}]
   :builds [{:rule "join-frontmatter"
             :inputs (map frontmatter-path all-pages)
             :outputs joined-frontmatter
             :implicit ff}]})

; TODO: allow configuring :flower/url
(defn build-page
  ([page]
     {:rule "frontmatter"
      :inputs page
      :outputs (frontmatter-path page)
      :implicit ff}))

(defn page-frontmatter [page]
  (-> reflect/*metadata* :pages (get (str page))))

; NOTE: we look at frontmatter contents here, but we only register a dependency on `all-frontmatter.json` so that we don't have to rebuild when only the file contents changes.
(defn transform-page [page implicits]
  (let [meta-path (frontmatter-path page)
        depfile (add-ext meta-path "d")
        tmpfile (prepend-ext meta-path "transformed")
        final (->> page page-frontmatter :flower/path (/ public))]
    ; NOTE: if you have a custom build command you have to add a :build yourself
    {:rule "transform"
     :page page
     :inputs meta-path
     :outputs final
     :implicit (concat transformers implicits [joined-frontmatter])
     :tmpfile (escape-shell tmpfile)
     :depfile depfile}))

(def page-builds
  (let [split-pages (map build-page all-pages)
        transformed (map #(transform-page % sass-outputs) all-pages)]
    {:builds (concat split-pages transformed)}))

(defn static->build [path]
  {:rule (if (fs/directory? path) "mkdir" "link")
   :inputs path
   :outputs (/ public (remove-parent path))})
(def static-builds {:builds (map static->build (fs/glob "static" "**"))})

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
     :restat true
     :command (fmt "${flower_cli} configure")
     :description "rebuild build.ninja itself"}
    {:name "flower-meta"
     :command (if use-jar "cd .. && clojure -T:build uberjar" "cd .. && clojure -T:build native-dev")
     :description "rebuild flower itself"}
    {:name "flower-defaults"
     :restat true
     :command (fmt "cd ../defaults && ${flower_cli} configure")
     :description "rebuild default build.ninja"}
    {:name "mkdir"
     :command (str "mkdir -p " builddir)
     :description "create build dir"}
    {:name "link"
     :command "ln -f $in $out"
     :description "link $in into build dir"}
    {:name "frontmatter"
     :command (fmt "${flower_cli} jq -R --filename $in '{filename: $$filename, content: .}' < $in | ${flower_cli} split-frontmatter > $out")}
    {:name "sass"
     :command (fmt "sass --quiet $in $out; ${flower_cli} split-sass-dependencies $out <$source-map >$depfile")
     :description "compile Sass file $in to CSS"}]
   :builds
   [{:rule "ninja-meta"
     :generator true
     :outputs "build.ninja"
     ; TODO: maybe we need to nest pages in builddir so they don't conflict?
     :depfile (/ builddir "build.clj.d")
     :inputs (concat ["flower.edn" "build.clj" joined-frontmatter] ff
                     ; NOTE: normally this would need to include pages/, but we already depend on all-frontmatter and vim likes to create temporary files
                     (mapcat all-dirs ["templates" "expressions" "sass"]))}
    (when rebuild-flower
      {:rule "flower-meta"
        :outputs ff
        :inputs (concat (fs/glob "../flower" "**") defaults ["../native.clj" "../flower" "../deps.edn"])})
    (when rebuild-flower
       {:rule "flower-defaults"
        :outputs "../defaults/build.ninja"
        :inputs "../defaults/build.clj"})
    {:rule "mkdir"
     :outputs builddir}]})

(def transss
  ["render" "markdown" "highlight" "embed"])

(defn trans-order [p]
  (let [i (->> p fs/file-name fs/strip-ext (.indexOf transss))]
    (if (= -1 i) nil i)))

(defn trans-sorter [left right]
  (let [[lscore rscore :as scores] (map trans-order [left right])]
    (cond
      (every? some? scores) (apply compare scores)
      (some? lscore) -1
      (some? rscore) 1
      ; alphabetical
      :else (compare left right))))

(def transform
        ; TODO: shell escaping
  (let [files (->> transformers (sort trans-sorter) (map str) (str/join " "))
        ; well this kinda sucks. $in is quoted but $depfile is not, so we can't use it.
        ; instead we assume it's always relative to $in.
        cmd (str "flower transform < $in --depfile $in.d --out-file $out "
                 "--transform-map $transform-map --all-frontmatter $all-frontmatter "
                 "$transformers > $tmpfile && flower jq -r .content < $tmpfile > $out")]
    {:variables {:transformers files
                 :transform-map (-> {} json/write-str escape-shell)
                 :all-frontmatter joined-frontmatter}
     :rules [{:name "transform"
              :command cmd
              :description "run all transformers on $page"}]}))

(expressions.ninja/generate
  (merge-deep all-frontmatter page-builds transform static-builds sass-builds base))
