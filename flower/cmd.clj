(ns flower.cmd
  (:use flower.internal.utils)
  (:require
   [babashka.fs :as fs]
   [clojure.data.json :as json]
   [clojure.set :refer [union]]
   [clojure.string :as str]
   [flower.eval :as eval]
   [flower.frontmatter :refer [split-frontmatter]]
   [flower.reflect]
   [flower.utils :refer [md->html]]
   [jq.api :as jq])
  (:import
   (java.io StringWriter)))

; utils

(defn- load-meta [f]
  (let [content (-> f fs/file slurp)
        split (split-frontmatter {:filename f :content content})]
   [(str f) (:frontmatter split)]))

; TODO: allow pages/index.edn so we can avoid repeating configuration
(defn- load-all-meta [dir]
  (let [paths (fs/glob dir "**")
        files (filter #(not (fs/directory? %)) paths)]
    (into {} (map load-meta files))))

; dependency tracking

; The following is quoted from ninja/src/depfile_parser.in.cc:
;
; Rather than implement all of above, we follow what GCC/Clang produces:
; Backslashes escape a space or hash sign.
; When a space is preceded by 2N+1 backslashes, it is represents N backslashes
; followed by space.
; When a space is preceded by 2N backslashes, it represents 2N backslashes at
; the end of a filename.
; A hash sign is escaped by a single backslash. All other backslashes remain
; unchanged.
(defn escape-depfile
  [s]
  ; NOTE: \ has to come first
  (let [specials "\\ #:%*~$"]
    (reduce #(str/replace %1 (str %2) (str "\\" %2)) s specials)))

(defn gen-depfile
  [out deps]
  (let [out (escape-depfile out)
        deps (->> deps (map escape-depfile) (str/join " "))]
        (fmt "${out}: ${deps}")))

(defn split-dependencies
  [parsed {:keys [depfile out-file]}]
  (let [[parsed deps] (split-map parsed :dependencies)
        formatted (gen-depfile out-file (:dependencies deps))]
    (spit depfile formatted)
    ; NOTE: we intentionally don't write to `out-file`, build.ninja is doing that.
    parsed))

; TODO: take out-dir as an arg
(defn split-sass-dependencies
  [parsed {:keys [source-file]}]
  (let [out-dir "public"
        deps (:sources parsed)
        relative-deps (map #(fs/relativize "." (str out-dir "/" %)) deps)]
    (gen-depfile source-file relative-deps)))

; meta-build system

(defn configure
  "Run `build.clj` to generate a build.ninja and save the output to disk."
  [{:keys [build-dir] :or {build-dir ".build"}}]
  (let [in (str *site* "/build.clj")
        out (str *site* "/build.ninja")
        ninja-writer (new StringWriter)
        page-meta (load-all-meta "pages")
        ; TODO: every time we hard-code a dir it makes things unconfigurable, figure out what to do
        template-meta (load-all-meta "templates")
        frontmatter {:pages page-meta :templates template-meta}
        dst (fs/path out)]
    (binding [flower.reflect/*ninja* ninja-writer
              flower.reflect/*frontmatter* frontmatter
              flower.reflect/*dependencies* #{}]
      (let [cx (eval/create-fs-cx in)
            embedded (str "(do" (slurp in) ")")
            lisp (eval/parse-string cx embedded)
            ; TODO: we need a mechanism for build.clj to pass back the builddir.
            ; maybe we can bind `flower.reflect/*build*` or something idk
            ; alternatively we can force this to be in flower.edn?
            depfile (fs/path *site* build-dir "build.clj.d")]
        (eval/eval-form cx embedded lisp)
        (fs/create-dirs build-dir)
        (let [contents (gen-depfile out flower.reflect/*dependencies*)]
          (fs/write-bytes depfile (String/.getBytes contents))))
    (->> ninja-writer str .getBytes (fs/write-bytes dst)))))

; jq emulator

; the clojure library is buggy and the underlying java library is hideously complicated.
; rather than try to figure out their api, just parse and reserialize the string.
(defn jq
  [{:keys [data query raw-input raw-output] :as m}]
  (let [in (if raw-input (json/write-str data) data)
        vars (dissoc m :data :query :raw-input :raw-output)
        res (try (jq/execute in query {:vars vars})
                 (catch net.thisptr.jackson.jq.exception.JsonQueryException e
                   (fatal "failed to run jq query:" (ex-message e))))]
    (if raw-output (json/read-str res) res)))

; preprocessing

; TODO: this can just be a normal transfomer
; actually no it needs to know the input languages and run them in sequence;
; see comment on render-file
(defn render-page
  "Preprocess and render a JSON blob"
  ([parsed {:keys [bindings] :or {bindings {}}}]
   (let [all-bindings (merge-deep {'frontmatter (:frontmatter parsed)} bindings)
         rendered (eval/render-file (:content parsed) (:filename parsed) all-bindings)]
     {:content rendered
      :frontmatter (:frontmatter parsed)})))

; index preprocessing

; TODO: horrible layering violation, doesn't handle expressions/constants.clj
; TODO: probably we should get this from stdin somehow?
(defn- get-src-dst
  [frontmatter]
  (let [base (-> frontmatter remove-parent remove-ext)]
    [(str "pages/" base) (str (remove-ext base) ".html")]))

(defn- index-page-meta
  [frontmatter all-meta]
  (let [[src dst] (get-src-dst frontmatter)
        base-meta (get all-meta src)]
    (update base-meta :path (constantly dst))))

(defn render-index
  "Preprocess and render a JSON blob as an index page (i.e. with access to `pages` local)"
  [parsed]
  ; TODO: put this on disk and feed it on stdin so we don't have to trust template renderers about dependency tracking.
  ; then we can move this to flower.build
  ; TODO: pass the name of the current index as a CLI arg so we can filter it out from locals
  ; NOTE: if we want a default index.html, we cannot allow index.html to be generated by a custom command.
  ; document that you should use `include` if you want that.
  ; TODO: document that custom commands cannot generate the same output file as a page
  ; TODO: this only works for post-processed pages; fix it to run `ninja -t targets | grep ^public`
  ; TODO: rename this to `render-page` and remove the existing render-page.
  ; it's fine for all pages to have access to all metadata, and it means we don't need to rebuild build.ninja
  ; whenever a page changes.
  ; TODO: once we do that, it's silly to parse this over and over in `get-all-meta`. cache it on disk with build.clj.
  (let [all-meta (load-all-meta "pages")
        all-targets (parse-ninja "ninja -t targets rule frontmatter" false)
        pages (map #(index-page-meta % all-meta) all-targets)]
    (render-page parsed {:bindings {'pages pages}})))

; TODO: this is silly lol, is this really the easiest way?
; maybe we can have `transformers/preprocessors` and `transformers/renderers` or something
(defn render-markdown
  "Render a markdown file to HTML"
  [parsed]
  (update parsed :content md->html))

(defn with-tracked-deps [dependencies f]
    (binding [flower.reflect/*dependencies* #{}]
      (let [out-map (f)
            ; TODO: should be keyed by output file so we can minimize rebuilds
            ; TODO: something is wrong here, it's not tracking file reads in templates
            all-deps (union (set dependencies) flower.reflect/*dependencies*)]
        (assoc out-map :dependencies all-deps))))

; transforming
(defn run-transformer
  "Given a `{:content x :frontmatter y :transformer z}` map,
  run the clojure in file `:transformer` on `{:content :frontmatter}`."
  [page transformer]
  (let [cx-opts {:bindings {'page page}}
        cx (eval/create-sci-cx transformer cx-opts)
        ; NOTE: parse-string only parses a single form, so we have to wrap the file in `do`
        ; borkdude suggests running parse-next in a loop instead, see
        ; https://clojurians.slack.com/archives/C015LCR9MHD/p1755283534353819?thread_ts=1755274827.891389&cid=C015LCR9MHD
        f (str "(do " (slurp transformer) ")")
        transformer (eval/parse-string cx f)
        run-transform (eval/embed '(transform page))
        ; NOTE: order is important here, see https://technomancy.us/143
        lisp `(do ~transformer ~run-transform)]
    ; TODO: this discards metadata, allow the transformer to mutate metadata
    ; also allow returning just a string to inherit existing metadata
    {:content (eval/eval-form cx "" lisp)}))

(defn transform
  [parsed {:keys [transform-map transformers] :as args}]
  (when-not (-> transform-map keys count (= 1))
    (fatal "TODO: transformers other than clojure (API and docs)"))
  (let [transformed (reduce run-transformer parsed transformers)]
    (split-dependencies transformed args)))
