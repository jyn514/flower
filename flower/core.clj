; Portions copyright Masashi Iizuka under Eclipse Public License 2.0
; see https://github.com/liquidz/frontmatter
; (set! *warn-on-reflection* true)

(ns flower.core
  (:gen-class)
  (:use flower.internal.utils)
  (:import
    (java.io StringWriter)
    (org.jsoup.select Nodes))
(:require
  [instaparse.core :as insta]
  [sci.core :as sci]
  [babashka.fs :as fs]
  [babashka.process :as ps]
  [babashka.process.pprint] ; https://clojurians.slack.com/archives/CLX41ASCS/p1753986315453519
  [babashka.cli :as cli]
  [hiccup2.core :as h]
  [hiccup.util]
  [clojure.string :as str]
  [clojure.java.io :as io]
  [clojure.data.json :as json]
  [clojure.edn       :as edn]
  [yaml.core     :as yaml]
  [toml-clj.core :as toml]
  [flower.build :as build]
  [flower.select]
  [flower.hiccup]
  [flower.reflect]
  [flower.defaults]
  [flower.live-reload]
  [flower.utils]
  [flower.internal.utils]
  [jq.api :as jq]
  [nextjournal.markdown :as md]
  [nextjournal.markdown.transform :as md.transform]))

(def VERSION "0.0.1")

; sandboxing

(defn load-sci-file [file] 
  {:file file :source (slurp file)})

; very broken; (-> str) doesn't work
#_(defn load-fn
  "load user code on-demand"
  [{ns- :namespace}]
    (when (str/starts-with? "flower.user." (name ns-))
      (let [file (-> ns- name (str/split #"\.") last (str "lib/" ".clj"))]
        (load-sci-file file))))

; see sci/binding for how to allow overriding this
(def userns (sci/create-ns 'user))
; (defn copy-macro [sym] (sci/copy-var* sym userns))
; (defn copy-macro [sym] `(do ^:sci/macro (fn [_&form# _&env# & rest#] (~sym rest#))))
(defn copy-ns [ns]
  (let [binding (sci/create-ns ns)
        publics (ns-publics ns)
        bindings (update-vals publics #(sci/copy-var* % binding))]
    (with-meta bindings {:ns binding})))

(defn pprint [x]
  (cond (var? x) ""
        (hiccup.util/raw-string? x) (str x)
        (instance? Nodes x) (Nodes/.outerHtml x)
        (sequential? x) (apply str (map pprint x))
        :else (print-str x)))

(defn embed
  "given a quoted form, embeds it in a program that prints out the stringified value"
  [lisp]
  ; can't just use normal dequoting here. if there is a `(require)` that is used later in `lisp`,
  ; it won't be evaluated eagerly and we will get a resolution error.
  ; use `eval` to delay resolution.
  `(flower.internal/pprint (eval '~lisp)))

; don't bind compile-html{,-with-bindings}, they'll crash at runtime
(def hiccup-compiler
  (dissoc (copy-ns 'hiccup.compiler)
          'compile-html 'compile-html-with-bindings))
; hiccup/html emits calls to compile-html. change them to render-html.
(def hiccup-core
  (let [ns (copy-ns 'hiccup2.core)
        html (sci/copy-var flower.hiccup/html-2 (-> ns meta :ns))]
  (assoc ns 'html html)))

(defn create-sci-cx
  "Create SCI context with standard library and local variables"
  ([filename] (create-sci-cx filename {}))
  ([filename opts]
    (with-meta (sci/init (-> opts (merge-deep {
      ; :load-fn load-fn
      ; NOTE: dynamic vars are *not* bound, which means that e.g. `*html-mode*` will not see any changes in the guest.
      ; see https://clojurians.slack.com/archives/C015LCR9MHD/p1753046766042839?thread_ts=1753045763.706789&cid=C015LCR9MHD
      ; maybe we can figure out a way to find dynamic vars with `dir`? but that still doesn't help find all functions that use them…
      :namespaces {'hiccup2.core hiccup-core
                   'hiccup.util (copy-ns 'hiccup.util) 
                   'hiccup.compiler hiccup-compiler
                   'instaparse.core (copy-ns 'instaparse.core) 
                   'flower.utils flower.utils/bindings
                   'flower.select (copy-ns 'flower.select)
                   'flower.internal {'pprint pprint}
                   'nextjournal.markdown (copy-ns 'nextjournal.markdown)}
      :bindings {'html (sci/copy-var flower.hiccup/html-2 userns)
                 'fmt (sci/copy-var fmt userns)
                 'md->html flower.utils/md->html}
      :classes {'java.lang.StringBuilder java.lang.StringBuilder}}) )) {:filename filename})))

; rendering

(defn print-sci-frame [f default-file]
  (let [var (str (:ns f) "/" (or (:name f) "<top-level>"))
        file (or (:file f)
                 ; TODO: this only catches the clojure runtime,
                 ; not bound flower functions
                 (if (:sci/built-in f)
                   "<host code>"
                   default-file)) 
        ; TODO: translate these to be relative to the template file, not the form start
        line (:line f)
        column (:column f)
        span (cond
               (and line column) (str " " line ":" column)
               line (str " " line)
               :else "")]
        (fmt "[${var} ${file}${span}]\n")))

(defn print-sci-trace [e default-file]
  (let [useful? #(or (:name %) (:line %) (not= (:ns %) 'user))
        ; TODO: don't print anything starting from host eval
        ; TODO: don't print out clojure.core/{let,fn} - those happen during name res and are never useful
        useful-frames (dedupe (filter useful? (sci/stacktrace e)))]
    (apply error
          "failed to run interpreted clojure:"
          (ex-message e)
          "\n"
          (map #(print-sci-frame % default-file) useful-frames))))

(defn eval-form
  "form eval. innermost function; use this instead of sci/eval-form directly."
  [cx form]
  (sci/binding [sci/out *err*
                sci/err *err*]
    ; TODO: render tracebacks nicely
    ; TODO: give a better error message for native libs that use eval
    (try (sci/eval-form cx form)
         (catch clojure.lang.ExceptionInfo e
           ; TODO: env variables suck lmao, do something else
           (if (System/getenv "FLOWER_HOST_TRACE")
             (throw e)
             (print-sci-trace e (-> cx meta :filename)))))))

(defn seval "string eval" [cx s]
  (->> s (sci/parse-string cx) embed (eval-form cx)))

; TODO: allow weird syntax in front of Ident (maybe Atom+ or something)
; https://clojure.org/reference/reader
; this is tricky because `#_id` needs to parse as [:Syntax "#_"], _ can't be associated with the ident
; maybe add a Syntax rule:
; Syntax = #\"[\\[\\;@^#`~']\"
(def parse
   (insta/parser
     "Start = (Text | Lisp)*
      Text = #'[^◊]+'
      Lisp = <'◊'> Form
      Form = (Ident | Syntax* List)
      Syntax = #\"[\\[\\;@^#`~']\"
      Ident = #'[a-zA-Z0-9_/.-]+'
      List = <'('> (Atom | List)* <')'>
      Atom = #'[^()]+' "))

(defn teval
  ([tree src] (teval tree src (create-sci-cx (-> src meta :filename))))
  ([tree src cx]
      (insta/transform {
        :Start str
        :Text identity
        :Ident #(seval cx %)
        ; str? if this was an Ident
        :Lisp #(if (string? %) %
                (seval cx (apply subs src (insta/span %))))
      } tree)))

(defn render
  "Render content with local variables available"
  ([src filename] (render src filename {}))
  ([src filename locals]
   ; TODO: also bind locals in `flower.locals`
   (let [cx (create-sci-cx filename {:bindings locals})]
    (teval (parse src) src cx))))

(defn load-meta [f]
  (let [content (-> f fs/file slurp)]
    (build/split-frontmatter {:filename f :content content}) :frontmatter))

; TODO: allow configuring :url
(defn load-all-meta [dir]
  (let [paths (fs/glob dir "**")
        files (filter #(not (fs/directory? %)) paths)]
    (map load-meta files)))

; preprocessing

(defn render-page
  "Preprocess and render a JSON blob"
  ([parsed {:keys [locals] :or {locals {}}}]
   (let [locals (merge-deep {'frontmatter (:frontmatter parsed)} locals)
         rendered (render (:content parsed) (:filename parsed) locals)]
     {:content rendered
      :frontmatter (:frontmatter parsed)})))

; index preprocessing

(defn get-meta
  [out all-meta]
  (let [orig_path (fs/path "pages" (build/remove-parent out))
        frontmatter (get all-meta orig_path {})]
    {:frontmatter frontmatter :path out}))

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
  (let [meta (load-all-meta "pages")
        ; TODO: use parse-ninja here
        out (:out (run {:out :string} "ninja -t targets rule transform"))
        ; handle empty string
        pages (if (seq out)
                (map #(update (get-meta % meta) :path build/remove-parent)
                     (str/split out #"\n"))
                {})]
    (render-page parsed {:locals {'pages pages}})))

; transforming
(defn transform
  "Given a `{:content x :frontmatter y :transformer z}` map,
   run the clojure in file `:transformer` on `{:content :frontmatter}`."
  [parsed {:keys [transformer]}]
  (let [locals {'page parsed}
        ; TODO: this is a mess lmao, straighten out my dependencies so i can just
        ; put `(def render flower.render/render)` in reflect.clj
        reflect (assoc (copy-ns 'flower.reflect) 'render render)
        cx-opts {:bindings locals #_:load-fn #_load
                 :namespaces {'flower.reflect reflect}}
        cx (create-sci-cx transformer cx-opts)
        f (slurp transformer)
        ; NOTE: parse-string only parses a single form, so we have to wrap the file in `do`
        ls (str "(do " f ")")
        transformer (sci/parse-string cx ls)
        lisp (embed (list 'do transformer '(transform page)))]
    (binding [flower.reflect/*dependencies* #{}]
      (let [html (eval-form cx lisp)]
        ; TODO: should be keyed by output file so we can minimize rebuilds
        (merge parsed {:content html :dependencies flower.reflect/*dependencies*})))))

(defn split-dependencies
  [parsed {:keys [depfile out-file]}]
  (let [[parsed deps] (split-map parsed :dependencies)
        joined (build/join (:dependencies deps))
        formatted (fmt "${out-file}: ${joined}")]
    (spit depfile formatted)
    ; NOTE: we intentionally don't write to `out-file`, build.ninja is doing that.
    parsed))

; meta-build system

(defn create-fs-cx
  [filename]
  (let [fs (copy-ns 'babashka.fs)
        build (copy-ns 'flower.build)]
    (create-sci-cx filename
      {:namespaces
       ; TODO: sandboxing
       {'babashka.fs fs
        'fs fs
        'flower.build build
        'build build}})))

(defn configure
  "Run `build.clj` to generate a build.ninja and save the output to disk."
  []
  (let [in (str *site* "/" "build.clj")
        out (str *site* "/" "build.ninja")
        ninja-writer (new StringWriter)
        page-meta (load-all-meta "pages")
        ; TODO: every time we hard-code a dir it makes things unconfigurable, figure out what to do
        template-meta (load-all-meta "templates")
        frontmatter {:pages page-meta :templates template-meta}
        dst (fs/path out)]
    (binding [flower.build/*ninja* ninja-writer
              flower.build/*frontmatter* frontmatter]
      (let [cx (create-fs-cx in)
            embedded (str "(do" (slurp in) ")")
            lisp (sci/parse-string cx embedded)]
        (eval-form cx lisp)))
    (->> ninja-writer str .getBytes (fs/write-bytes dst))))

; template embedding
(defn embed-template
  "Given a {:content :frontmatter} page and the name of a template file,
  render `template` in context."
  [embed {:keys [template-name]}]
        ; TODO: layering violation, we shouldn't be reading this off disk.
        ; instead we should run `split-frontmatter` on the template too
        ; and then merge the two.
  (let [template-contents (-> template-name slurp)
        {template-frontmatter :frontmatter template :content}
          (build/split-frontmatter {:filename template-name :content template-contents})
        frontmatter (merge-deep template-frontmatter (:frontmatter embed))
        locals {'content (:content embed)
                'frontmatter frontmatter}
        embedded (render template template-name locals)]
    {:content embedded
     :frontmatter frontmatter}))

; jq emulator

; the clojure wrapper sucks and is poorly documented, so just use the Java one
; Helper interface that specifies a method to get a string value.
#_(definterface IContainer
  ; net.thisptr.jackson.jq/Output
  (^java.lang.Iterable getValue []))

; (deftype give-me-the-damn-data [JsonNode the-data]
;   Output
;   (emit [this json-node] (set! (. this the-data) json-node)))
;
; (defn jq [data query]
;   (let [scope (Scope/newEmptyScope)
;         compiled (JsonQuery/compile query Versions/JQ_1_6)
;         tree (.readTree (ObjectMapper.) data)
;         ; s (java.io.StringWriter.)
;         s (give-me-the-damn-data. nil)
;         out (.apply compiled scope tree s)]
;     s))

; the clojure library is buggy and the underlying java library is hideously complicated.
; rather than try to figure out their api, just parse and reserialize the string.
(defn jq
  [{:keys [data query raw-input raw-output] :as m}]
  ; (eprn m)
  (let [in (if raw-input (json/write-str data) data)
        res (try (jq/execute in query)
                 (catch net.thisptr.jackson.jq.exception.JsonQueryException e
                   (error "failed to run jq query:" (ex-message e))))]
    (if raw-output (json/read-str res) res)))

; CLI and IO

(defn map-json
  "Given a function `f` that transforms a clojure map to a clojure map,
   read the map as JSON from stdin and write it to stdout.
   If any `args` are present, they will be passed after the map."
  [f & args]
  ; TODO: https://clojure.atlassian.net/browse/DJSON-43
  (let [reader (java.io.PushbackReader. *in* 64)
        before (try (json/read reader :key-fn keyword)
                    (catch java.io.EOFException e
                      (error "failed to parse JSON:" (ex-message e))))
        after (apply f before args)]
    (json/write after *out*)))

(defn help []
  (error "help is not yet implemented, sorry"))

(defn no-args [f]
  (fn [& _] (f)))

(defn unknown-command [{:keys [args] :as m}]
  (error (str "unrecognized command: '"
              (str/join " " args)
              "' (-h for help, or 'watch' to build your site)")))

(def dispatch-table
  {"configure" (no-args configure)
   "split-frontmatter" (no-args #(map-json build/split-frontmatter))
   "render-page" (no-args #(map-json render-page {}))
   "render-index" (no-args #( map-json render-index ))
   "embed-template" {:fn #( map-json embed-template %)
                     :coerce {:template-name :string}
                     :args->opts [:template-name]}
   "transform" {:fn #( map-json transform %)
                     :coerce {:transformer :string}
                     :args->opts [:transformer]}
   "split-dependencies" {:fn #(map-json split-dependencies %)
                         :coerce {:depfile :string :out-file :string}
                         :args->opts [:depfile :out-file]}
   "watch" flower.live-reload/watch
   "new" (no-args flower.defaults/materialize-all)
   ; TODO: this overrides --data
   "jq" {:fn #(println (jq (assoc % :data (slurp *in*))))
         :coerce {:raw-input :boolean :raw-output :boolean
                  :data :string :query :string}
         :aliases {:R :raw-input :r :raw-output}
         :args->opts [:query]}
   ["version" "--version"] (no-args #(println VERSION))
   ["help" "--help" "-h"] (no-args help)
   [] {:fn unknown-command :needs-metadata true}})

(defn ->bb
  "Convert our `dispatch-table` DSL to babashka/dispatch syntax.

  `init` is a function that will run before the dispatched command
  to set up global options. It takes two arguments:
  the function to run inside globals and the parsed options.
  It should pass the options as an argument to the function."
  [init key val]
  (if (and (vector? key) (seq key))
    (for [cmd key] (->bb init cmd val))
    (let [cmds (if (string? key) [key] key)
          [my-fn opts] (if (map? val) [(:fn val) val] [val {}])
          wrapped-fn (if (:needs-metadata opts) my-fn #(my-fn (:opts %)))]
      (assoc opts :cmds cmds :fn #(init wrapped-fn %)))))

(defn dispatch-cmd
  "Parse the CLI args and dispatch to the appropriate clojure funciton.
  Also registers global options."
  [args]
  (let [init #(binding [*site* (or (get-in %2 [:opts :C]) ".")]
                (%1 %2))
        table (map #(apply ->bb init %) dispatch-table)]
    (cli/dispatch table args {:coerce {:C :string}})))

(defn -main [& args]
  (try
    (dispatch-cmd args)
    (catch clojure.lang.ExceptionInfo e
      (if (and (-> e ex-data :flower/exit)
               (not (System/getenv "FLOWER_HOST_TRACE")))
        (do
          (eprintln (ex-message e))
          (System/exit 1))
        (throw e)))
    (finally
      (shutdown-agents)
      (flush))))
