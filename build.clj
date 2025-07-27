(require 'flower.build
         '(babashka [fs :as fs])
         '(clojure [string :as str]))
(use 'flower.utils)

(defn / [x & more]
  (apply fs/path x more))

(defn ext [path ext]
  (-> path fs/path fs/file-name fs/strip-ext (str "." ext)))

(def public "public")
(def builddir ".build")
(def templates "templates")
(def f "flower")
(def ff
  "flower files"
  (conj
    (map #(/ f (ext % "clj"))
         ["parse" "select" "utils" "build"])
    "deps.edn"))
(def flower_cli "clj -M --main flower.core")
(defn flow [cmd] (fmt "${flower_cli} ${cmd} <$in >$out"))

(defn build-page [page]
  (let [template (-> (get flower.build/all-frontmatter page) :template (or "default.html"))
        template_path (/ templates template)
        json_frontmatter (/ builddir (ext page "md.json"))
        processed_markdown (/ builddir (ext page "md.rendered.json"))
        ; can be different than embedded_html if e.g. the template ends in .md
        embedded_markdown (/ builddir (ext page (str "embed." (fs/extension template))))
        embedded_html (/ builddir (ext page "embed.html"))
        final_html (/ builddir (ext page "html"))
        rules [{:rule "frontmatter"
                :inputs (str page)
                :outputs json_frontmatter
                :implicit ff}
               {:rule "page"
                :inputs json_frontmatter
                :outputs processed_markdown}
               {:rule "template"
                :inputs processed_markdown
                :outputs embedded_markdown
                :implicit (conj ff template_path)
                :template template_path}
               {:rule "postprocess"
                :inputs embedded_html
                :outputs final_html
                :implicit (fs/glob "postprocessors" "*")
                :html embedded_html}]]
    {:rules rules :out embedded_markdown}))

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

(def page-builds
  (mapcat
    #(let [{:keys [rules out]} (build-page %)]
       (chain-commands out rules))
    ; #(apply chain-commands (build-page %))
    all-pages))

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
     :command "ln -f $in $out"
     :description "link $in into build dir"}
    {:name "page"
     :command (flow "render-page")
     :description "render $in using clojure"}
    {:name "template"
     ; NOTE: this means that all templates must depend on all other templates
     :command (fmt "${flower_cli} embed-template $template < $in > $out")
     :description "embed $in into $template using clojure"}
    ; {:name "postprocess"
    ;  :command (flow "postprocess")
    ;  :description "transform $html with $postprocessor using clojure"}
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

(defn postprocess [{runners :all-postprocessors}]
  (let [pps (fs/glob "postprocessors" "*")
        cmds (map #(str (get runners (fs/extension %)) " " %) pps)
        pipe (str/join " | " cmds)
        cmd (fmt "< $in ${pipe} | ${flower_cli} jq .content > $out")]
  {:rules [{:name "postprocess"
            :command cmd
            :description "run all postprocessors on $in"}]}))

(flower.build/register-postprocessor-runners
  {"clj" (str flower_cli " postprocess")})
(flower.build/generate
  (update-in base [:builds] #(concat % page-builds))
  postprocess)
