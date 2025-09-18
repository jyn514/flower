(ns flower.cmd
  (:use flower.utils)
  (:require
   [babashka.fs :as fs]
   [clojure.core.reducers :as r]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [flower.eval :as eval]
   [flower.frontmatter]
   [flower.defaults :refer [path-considering-vfs]]
   [flower.reflect])
  (:import
   (java.io PushbackReader StringWriter)))

; utils

(defn write-if-modified
  "Used by build.clj to make :restat work"
  {:malli/schema [:-> :string :any :nil]}
  [data path]
  (let [fsize (try (fs/size path)
                   (catch java.lang.Exception _ -1))
        eq (and (= fsize (count data)) (= data (slurp path)))]
    (when-not eq
      (spit path data))))

(defn read-json [file desc]
        ; TODO: https://clojure.atlassian.net/browse/DJSON-43
  (let [reader (java.io.PushbackReader. file 64)]
    (try (json/read reader :key-fn keyword)
         ; bruh what is up with the json parser not having scoped exceptions
         (catch java.lang.Exception e
           (fatal (fmt "failed to parse JSON in ${desc}:") (ex-message e))))))

(defn read-stdin-json []
  (read-json *in* "stdin"))

(defn write-json [data]
        ; preserve namespaces in output
  (let [serialize #(cond (keyword? %) (subs (str %) 1)
                         (symbol? %) (name %)
                         :else (str %))]
    (json/write data *out* :key-fn serialize)))

(defn read-json-file [path]
  (read-json (-> path fs/file io/reader) (str path)))

(defn- load-meta [f]
  (let [content (-> f fs/file slurp)
        parsed (flower.frontmatter/split-frontmatter {:filename f :content content})]
   [(str f) (:frontmatter parsed)]))

; TODO: allow pages/index.edn so we can avoid repeating configuration
; NOTE: unlike most of flower, does not consider VFS
(defn- load-all-meta [dir]
  (let [paths (fs/glob dir "**")
        files (filter #(not (fs/directory? %)) paths)]
    (into {} (map load-meta files))))

; dependency tracking

(defn split-dependencies
  [dependencies {:keys [depfile out-file]}]
  (when (some nil? [depfile out-file dependencies])
    (throw (ex-info (str "got <nil> when trying to write a depfile for " out-file) {})))
  (let [formatted (gen-depfile out-file dependencies)]
    (spit depfile formatted)))

(defn load-settings [registry cli list-settings]
  (when list-settings
    (msg "options available:")
    (doseq [[name opt] registry]
      (printf "  %s: %s (default: %s)\n" name (:help opt) (pr-str (:default opt))))
    (flush)
    (System/exit 0))
  (let [default-vals (into {} (for [[k {:keys [default]}] registry]
                                [k default]))
        keyss #(into #{} (keys %))
        unknown-opts (set/difference (keyss cli) (keyss default-vals))]
    (when (seq unknown-opts)
      (warn "unknown" (str (pluralize unknown-opts "option") ":")
            (str/join ", " unknown-opts)))
    (merge default-vals cli)))

; meta-build system

(defn configure
  "Run `build.clj` to generate a build.ninja and save the output to disk."
  [{settings :set, list-settings :list, :keys [build-dir] :as opts
    :or {build-dir ".build"}}]
  (when-not (fs/exists? "flower.edn")
    (fatal "this doesn't look like a flower site. Consider running `flower new` first."))
  (let [defaults (fs/path build-dir "defaults")]
    (when-not (fs/exists? defaults)
      (flower.defaults/materialize-all opts)))
  (binding [flower.reflect/*dependencies* #{}]
    (let [global-meta (with-open [fd (io/reader (str *site* "/flower.edn"))]
                        (edn/read (PushbackReader. fd)))
          settings (load-settings (:settings global-meta) settings list-settings)
          in (str (path-considering-vfs "build.clj"))
          out (str *site* "/build.ninja")
          ; TODO: load this from flower.edn
          ; TODO: wow this sucks :(
          ninja-writer (new StringWriter)
          page-meta (load-all-meta "pages")
          all-meta {:pages page-meta
                    :settings settings}]
      (binding [flower.reflect/*ninja* ninja-writer
                flower.reflect/*metadata* all-meta]
        (let [cx (eval/create-sci-cx in)
              embedded (str "(do" (slurp in) ")")
              lisp (eval/parse-string cx eval/start-span embedded)
              ; TODO: we need a mechanism for build.clj to pass back the builddir.
              ; maybe we can bind `flower.reflect/*build*` or something idk
              ; alternatively we can force this to be in flower.edn?
              depfile (fs/path *site* build-dir "build.clj.d")]
          (eval/eval-form cx embedded lisp)
          (fs/create-dirs build-dir)
          (let [contents (gen-depfile out flower.reflect/*dependencies*)]
            (fs/write-bytes depfile (String/.getBytes contents))))
        (-> ninja-writer str (write-if-modified out))))))

(defn run-configure [opts]
  ; TODO: doesn't handle the case where the exception trickles up to main.
  ; probably that's fine though
  (binding [*cmd* " configure"]
    (configure opts)))

(defn build [opts]
  (run-configure opts)
  (system! "ninja"))

; frontmatter utils

(defn split-frontmatter
  [opts]
  (write-json
    (flower.frontmatter/split-frontmatter
      (assoc opts :content (slurp *in*)))))

(defn join-frontmatter
  [{files :path out :out-file}]
  (let [read-frontmatter #(:frontmatter (read-json-file %))
        merged (r/foldcat (pmap read-frontmatter files))
        json (with-out-str (write-json merged))]
    (write-if-modified json out)))

; preprocessing

; index preprocessing

(defn with-tracked-deps [f]
  (binding [flower.reflect/*dependencies* #{}]
    (let [out-map (f)]
      ; TODO: should be keyed by output file so we can minimize rebuilds
      [out-map flower.reflect/*dependencies*])))

; transforming
(defn run-transformer
  "Given a `{:content x :frontmatter y :transformer z}` map,
  run the clojure in file `:transformer` on `{:content :frontmatter}`."
  [{:keys [raw-output] :as opts} all-frontmatter page transformer last]
  (let [page (merge (select-keys page [:content :frontmatter]) {:variables (dissoc opts :raw-output)})
        bindings {'page page
                  'pages all-frontmatter}
        cx-opts {:bindings bindings
                 :namespaces {'flower.locals bindings}}
        trans-path (str (path-considering-vfs transformer))
        cx (eval/create-sci-cx trans-path cx-opts)
        ; NOTE: parse-string only parses a single form, so we have to wrap the file in `do`
        ; borkdude suggests running parse-next in a loop instead, see
        ; https://clojurians.slack.com/archives/C015LCR9MHD/p1755283534353819?thread_ts=1755274827.891389&cid=C015LCR9MHD
        f (str "(do " (slurp trans-path) ")")
        transformer (eval/parse-string cx eval/start-span f)
        ; NOTE: does *not* call pretty-print
        run-transform '(transform flower.locals/page)
        ; NOTE: order is important here, see https://technomancy.us/143
        lisp `(do ~transformer ~run-transform)
        transformed (eval/eval-form cx f lisp)]
    ; for raw output transformers, trust them to return exactly what they say
    (if (and raw-output last) transformed
      ; if a transformer returns a string, preserve existing metadata
      (if (string? transformed) (assoc page :content transformed)
        ; otherwise, allow overriding :content, and any frontmatter except :source-file
        (let [sandboxed (select-keys transformed [:content :frontmatter])
              moar-sandboxed (update sandboxed :frontmatter
                                     #(merge (:frontmatter page) %
                                             (select-keys page [:flower/source-file])))]
          (merge page moar-sandboxed))))))

(defn transform
  [{:keys [transform-map transformers all-frontmatter
           standalone raw-input raw-output]
    :as opts}]
  (when-not (-> transform-map keys count (= 0))
    (fatal "TODO: transformers other than clojure (API and docs)"))
  (let [frontmatter (when-not standalone (read-json-file all-frontmatter))
        last (dec (count transformers))
        bindings (dissoc opts :transform-map :transformers :all-frontmatter
                              :standalone :raw-input)
        run (fn [page [i t]] (run-transformer bindings frontmatter page t (= last i)))
        before (if raw-input (slurp *in*) (read-stdin-json))
        after (reduce run before (enumerate transformers))]
    (if raw-output
      (do
        (when-not (string? after)
          (fatal "--raw-output is only valid when the transformed output is a raw string"))
        (print after))
      (write-json after))))
