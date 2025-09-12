(ns flower.eval
  (:use flower.utils)
  (:require
   [babashka.fs]
   [clj-commons.digest]
   [clojure.data.json]
   [clojure.java.io :as io]
   [clojure.repl :as repl]
   [clojure.string :as str]
   [clojure.walk :refer [postwalk]]
   [flower.defaults :refer [path-considering-vfs]]
   [flower.hiccup]
   [flower.reflect]
   [flower.fs]
   [flower.unsafe]
   [hiccup.util]
   [hiccup2.core]
   [instaparse.core :as insta]
   [java-time.api]
   [nextjournal.markdown]
   [sci.core :as sci]
   [sci.impl.callstack])
  (:import
   [java.io BufferedReader StringReader]
   [org.jsoup.nodes Document]
   (org.jsoup.select Nodes)))

; types

(def Span [:map [:line :int] [:column :int]])
(def start-span {:line 0 :column 0})

; sandboxing

(def ^{:dynamic true :private true} *cx* "only for use by render-page" nil)

(defn load-sci-file [file] 
  (set! flower.reflect/*dependencies* (conj flower.reflect/*dependencies* file))
  {:file file :source (slurp file)})

(defn load-fn
  "load user code on-demand"
  [{ns- :namespace}]
    (when (or (str/starts-with? (name ns-) "expressions.")
              (str/starts-with? (name ns-) "transformers."))
      (let [as-path (str/replace ns- "." "/")
            file (str as-path ".clj")]
        (-> file path-considering-vfs str load-sci-file))))

; see sci/binding for how to allow overriding this
(def userns (sci/create-ns 'user))
(defn copy-ns
  ([ns] (copy-ns ns {}))
  ([ns {:keys [include-private dst symbols]}]
   (let [binding (or dst (sci/create-ns ns))
         considered-vars (cond
                           symbols (into {} (for [sym symbols] [sym (ns-resolve ns sym)]))
                           include-private (ns-map ns)
                           :else (ns-publics ns))
         ; copy-var* assumes that it can deref any var; make sure that's true
         vars (filter (fn [[_ v]] (instance? clojure.lang.IDeref v)) considered-vars)
         bindings (update-vals vars
                               #(sci/copy-var* % binding))]
     (with-meta bindings {:ns binding}))))

(defn pretty-print [x]
  (cond (or (instance? sci.lang.Var x) (nil? x)) ""
        (hiccup.util/raw-string? x) (str x)
        (instance? Document x) (Document/.outerHtml x)
        (instance? Nodes x) (Nodes/.outerHtml x)
        (sequential? x) (apply str (map pretty-print x))
        :else (print-str x)))

(defn embed
  "given a quoted form, embeds it in a program that prints out the stringified value"
  [lisp]
  (with-meta `(flower.eval/pretty-print ~lisp) (meta lisp)))

; don't bind compile-html{,-with-bindings}, they'll crash at runtime
(def hiccup-compiler
  (dissoc (copy-ns 'hiccup.compiler)
          'compile-html 'compile-html-with-bindings))
; hiccup/html emits calls to compile-html. change them to render-html.
(def hiccup-core
  (let [ns (copy-ns 'hiccup2.core)
        html (sci/copy-var flower.hiccup/html-2 (-> ns meta :ns))]
  (assoc ns 'html html)))

(def missing-core
  "missing from clojure.core in an SCI context;
  see https://clojurians.slack.com/archives/C015LCR9MHD/p1755400427739019?thread_ts=1755396527.247349&cid=C015LCR9MHD"
  #{'println-str 'abs 'infinite? 'iteration 'NaN?
    'parse-boolean 'parse-long 'parse-double
    'partitionv 'partitionv-all 'splitv-at
    'update-keys 'update-vals 'with-precision})

(def clojure-core-only-missing (copy-ns 'clojure.core {:symbols missing-core}))
(def clojure-core (assoc clojure-core-only-missing
                         'slurp (sci/copy-var flower.fs/slurp-
                                              (-> clojure-core-only-missing meta :ns)
                                              {:name 'slurp})))
; only pathlib
; NOTE: canonicalize and friends intentionally missing because they resolve symlinks
(def bb-fs
  #{'absolute? 'absolutize 'normalize 'unixify
    'relative? 'relativize 'components 'ends-with?
    'extension 'file-name 'file-separator 'parent 'path
    'path-separator 'split-ext 'strip-ext 'split-paths
    'starts-with?
    'expand-home 'home ; Technically impure but it's fine
    })

; (defn print-trace [ex]
;   (repl/print-trace ex false))

(declare render-file)
; needs to be a function, otherwise render-file won't be bound
(defn sci-defaults []
  {
   :load-fn load-fn
   ; NOTE: dynamic vars are *not* bound, which means that
   ; e.g. `*html-mode*` will not see any changes in the guest.
   ; see https://clojurians.slack.com/archives/C015LCR9MHD/p1753046766042839?thread_ts=1753045763.706789&cid=C015LCR9MHD
   ; maybe we can figure out a way to find dynamic vars with `dir`?
   ; but that still doesn't help find all functions that use them…
   :namespaces {; re-exported libs
                'clojure.core clojure-core
                'clojure.data.json (copy-ns 'clojure.data.json) 
                'nextjournal.markdown (copy-ns 'nextjournal.markdown)
                'hiccup2.core hiccup-core 
                'hiccup.util (copy-ns 'hiccup.util)
                'hiccup.compiler hiccup-compiler
                'instaparse.core (copy-ns 'instaparse.core)
                'clj-commons.digest (copy-ns 'clj-commons.digest)
                'java-time.api (copy-ns 'java-time.api)
                ; repl/doc tries to call private functions, so we need to copy those too
                ; TODO: SCI does this for us, apparently?
                ;'clojure.repl (copy-ns 'clojure.repl {:include-private true})
                ; internals
                'flower.eval {'pretty-print pretty-print}
                ; flower API
                'flower.fs (merge (dissoc (copy-ns 'flower.fs) 'slurp-)
                                  (copy-ns 'babashka.fs {:dst 'flower.fs
                                                         :symbols bb-fs}))
                'flower.reflect (assoc (copy-ns 'flower.reflect)
                                       'render-file render-file)
                ; TODO: use drop-bomb here too
                'flower.unsafe (dissoc (copy-ns 'flower.unsafe) '*drop-bomb*)
                ; 'babashka.fs (copy-filtering 'babashka.fs bb-fs)
                'flower.unsafe.fs (copy-ns 'babashka.fs {:dst 'flower.unsafe.fs})}
   ; NOTE: the strings will give a class cast exception if someone tries to rebind them
   :bindings {'« "«"
              '» "»"
              '◊ "◊"
              '⋄ "⋄"
              ;'print-trace (sci/copy-var print-trace)
              'html (sci/copy-var flower.hiccup/html-2 userns)
              'fmt (sci/copy-var fmt userns)
              'doc (sci/copy-var repl/doc userns)
              'dir (sci/copy-var repl/dir userns)
              'source (sci/copy-var repl/source userns)}
   ; keep this in sync with `dynamic` in native.clj
   :classes {'java.lang.StringBuilder java.lang.StringBuilder
             'java.util.List java.util.List
             'java.util.regex.Pattern java.util.regex.Pattern
             'clojure.lang.PersistentVector clojure.lang.PersistentVector
             'java.time.format.DateTimeParseException java.time.format.DateTimeParseException
             'java.time.OffsetDateTime 'java.time.OffsetDateTime
             'org.jsoup.Jsoup org.jsoup.Jsoup
             'org.jsoup.select.Elements org.jsoup.select.Elements
             'org.jsoup.nodes.Node org.jsoup.nodes.Node
             'org.jsoup.nodes.Element org.jsoup.nodes.Element
             'org.jsoup.nodes.Comment org.jsoup.nodes.Comment
             'org.jsoup.nodes.TextNode org.jsoup.nodes.TextNode
             'org.jsoup.nodes.Document org.jsoup.nodes.Document
             'org.jsoup.nodes.DocumentType org.jsoup.nodes.DocumentType
             'org.jsoup.nodes.Attribute org.jsoup.nodes.Attribute
             'org.jsoup.nodes.Attributes org.jsoup.nodes.Attributes
             'org.jsoup.nodes.XmlDeclaration org.jsoup.nodes.XmlDeclaration
             'org.jsoup.parser.Parser org.jsoup.parser.Parser}})

(defn create-sci-cx
  "Create SCI context with standard library and local variables"
  ([filename] (create-sci-cx filename {}))
  ([filename opts]
   (let [cx (->> opts (merge-deep (sci-defaults)) sci/init)]
     (with-meta cx {:flower/filename filename}))))

; eval and tracebacks

(defn offset->line
  "Given a start byte offset and source string, return a Span."
  {:malli/schema [:-> [:maybe :int] :string Span]}
  [start src]
  (if start
    (let [[line-zero line] (->> (subs src 0 start)
                                StringReader.
                                BufferedReader.
                                line-seq
                                enumerate
                                last)]
      {:line line-zero :column (dec (count line))})
    {:line 0 :column 0}))

(defn abs-span
  "Make a `relative` span absolute with respect to `start`"
  {:malli/schema [:-> Span Span Span]}
  [{start-line :line start-column :column}
   {relative-line :line relative-column :column}]
  {:line (+ relative-line start-line)
   ; TODO: columns are messed up
   :column (if (= relative-line 1)
             (+ relative-column start-column)
             relative-column)})

(defn render-sci-frame [frame]
  (let [{:keys [line column ns name file]
         :or {name "<top-level>"
              file (if (:sci/built-in frame)
                   "<clojure-runtime>"
                   "<bound-host-function>")}} frame
        var (str ns "/" name)
        span (cond
               (and line column) (str " " line ":" column)
               line (str " " line)
               :else "")]
    (fmt " [${var} ${file}${span}]\n")))

(defn print-sci-trace [e stacktrace dup]
  (let [useful? #(or (:name %) (:line %) (not= (:ns %) 'user))
        ; TODO: don't print out clojure.core/{let,fn} - those happen during name res and are never useful
        useful-frames (dedupe (filter useful? stacktrace))]
    (when (and dup (not (instance? clojure.lang.ExceptionInfo dup)))
      (-> dup type pr-str (str ": ") print))
    (apply print
      (ex-message e)
      "\n"
      (map render-sci-frame useful-frames))))

(defn print-stack-trace [e]
  (if-let [sci-ex (sci/stacktrace e)]
      ; skip the inner error, sci duplicates messages >:(
      (let [inner (some-> e ex-cause ex-message)
            dup (= inner (ex-message e))]
        (print-sci-trace e sci-ex (when dup (ex-cause e)))
        (when dup
          (-> e ex-cause ex-cause)))
      (do
        ; TODO: make NoSuchFileExceptions relative to *site*
        (if (instance? clojure.lang.ExceptionInfo e)
          (println (ex-message e))
          (println (str (pr-str (class e)) ":") (ex-message e)))
        (when-let [file (-> e ex-data :flower/filename)]
          (let [span (-> e ex-data :flower/span)]
            (print "" (render-sci-frame (merge {:ns 'user :file file} span (meta e))))))
          (ex-cause e))))

(defn print-cause-trace [ex]
  (loop [e ex
         first-loop true]
    (when (not first-loop)
      (print " Caused by: "))
    (when-let [cause (print-stack-trace e)]
      (recur cause false))))

(defn map-ex-info
  "Updates `ex-data` for `ex`, preserving message, cause, and stack trace."
  [ex mapper]
  (let [info (-> ex ex-data mapper)
        new-ex (ex-info (ex-message ex) info (ex-cause ex))]
    (.setStackTrace ^clojure.lang.ExceptionInfo new-ex (.getStackTrace ^Throwable ex))
    new-ex))

(defn try-sci
  [cx f]
  ; TODO: give a better error message for native libs that use eval
  (try (f)
       (catch clojure.lang.ExceptionInfo cause
         (let [file (-> cx meta :flower/filename)
               span (-> cx meta :flower/span)
               msg (fmt "failed to eval ${file}")
               new-cause (map-ex-info cause
                           #(assoc %
                             :flower/filename file
                             :flower/span span))
               ex (ex-info msg {:flower/exit true
                                :flower/eval true} new-cause)]
           (throw ex)))))

(defn parse-string
  ([s] (parse-string *cx* start-span s))
  ([cx span s]
   (when (env "FLOWER_DEBUG_PARSE")
     (def ^:dynamic *lisp* s) ; for repl
     (eprint "parse-string: ")
     (eprn s))
   (let [cx (update-meta #(merge % {:flower/span span}) cx)]
     (try-sci cx #(sci/parse-string cx s)))))

(defn eval-form
  "form eval. innermost function; use this instead of sci/eval-form directly."
  ([src form] (eval-form *cx* src form))
  ([cx src form]
   (when (env "FLOWER_DEBUG_EVAL")
     (def ^:dynamic *form* form) ; for repl
     (eprint "eval-form: ")
     (eprn form))
   (let [cx (update-meta #(merge %
                                 {:flower/span (-> form insta/span first (offset->line src))}) cx)]
     (binding [*cx* cx]
       (sci/binding [sci/out *err*
                     sci/err *err*
                     sci/ns userns
                     sci/file (-> cx meta :flower/filename)]
         (try-sci cx #(sci/eval-form cx form)))))))

(defn ->abs
  "Given an SCI parsed form, update its metadata to be relative to `start`"
  {:malli/schema [:-> Span :any :any]}
  [start node]
  ; TODO: add an update-meta helper
  (let [relative (meta node)]
    (if (and (:line relative) (:column relative))
      (with-meta node (merge relative (abs-span start relative)))
      node)))

(defn parse-with-meta
  "Given a SCI context and one or more nodes,
   parse the source text spanned by the combined nodes.
   Then, recursively update all spans on the result to be relative 
   to the span of the nodes, not the substring of the source text."
  [cx src & nodes]
  (let [l (-> nodes first insta/span first)
        r (-> nodes last insta/span last)
        start (offset->line l src)
        orig_src (apply subs src [l r])
        mapped (parse-string cx start orig_src)]
    (postwalk #(->abs start %) mapped)))

; sunflower

(def parse-file "META-INF/resources/flower/eval/parser.ebnf")
(def parse (insta/parser (io/resource parse-file)))

(defn flower-call
  ([cx src list trailer] (flower-call cx src nil list trailer))
  ([cx src syntax list trailer]
   (let [parsed-args (if (nil? syntax)
                       (parse-with-meta cx src list)
                       (parse-with-meta cx src syntax list))
         merged-args (with-meta (concat parsed-args trailer)
                                (meta parsed-args))]
     (embed merged-args))))

(defn outer-ident
  ([cx src ident] (outer-ident cx src nil ident))
  ([cx src syntax ident]
   (let [parsed (if (nil? syntax)
                  (parse-with-meta cx src ident)
                  (parse-with-meta cx src syntax ident))]
     (embed parsed))))


(defn nested-render
  [src markup]
  ; first, add spans to all inner (str) calls
  ; NOTE: we leave the nested metadata be.
  ; we've already walked it once, no need to do it again.
  (let [span (-> markup insta/span first (offset->line src) (update-vals inc))]
    (if markup
      (with-meta `(str ~@markup) span)
      '())))

(defn transformer
  "'''IR''' (really just fancy parse-form)"
  [tree src cx]
  (insta/transform
    {:Start vector
     :Text identity
     :FlowerSyntax identity

     :OuterComment (constantly "")
     :OuterIdent #(apply outer-ident cx src %&)
     :FlowerCall #(apply flower-call cx src %&)

     ; don't have spans available yet; for now just combine them all into a vec
     :NestedRender vector
     :CallTrailer #(map (fn [m] (nested-render src m)) %&)
    } tree))

(defn- on-parse-event [cx src ev]
  (if (string? ev) ev
    (eval-form cx src ev)))

(defn teval
  "tree eval"
  ([tree src cx]
   (let [events (transformer tree src cx)]
     (apply str (map #(on-parse-event cx src %) events)))))

; TODO: needs to account for pages not in clojure
; TODO: should include metadata parsed from frontmatter
; actually hm. we don't need to deal with *preprocessing* other than clojure,
; or at least, `◊(render ...)` doesn't need to.
; we only need to deal with *markup languages* other than markdown.
; i think we need to split `render-in-context` from `render-file` and only expose the former through flower.reflect.
; for now i'm going to treat this as `render-in-context`, i'll write `render-file` later.
; render-file will need to:
; - look at frontmatter.preprocessors and run them in sequence
; - once all preprocessors have run, convert the markup language to html
; for now, hard-code the clojure preprocessor and language markdown.
; actually no, the markup renderer needs to live in build.clj so people can write custom commands.
(defn render-file
  "Render content with local variables available"
  ; TODO: this causes nothing but problems, replace it with an options map
  ([src filename] (render-file src filename {}))
  ([src filename locals]
   ; TODO: also bind locals in `flower.locals`
   ; NOTE: we have to use `new-var` here or using `def` on a bound local will crash SCI
   (let [bindings (into {} (for [[name val] locals]
                             [name (sci/new-var name val)]))
         opts {:namespaces {'user bindings 'flower.locals bindings}}
         cx (if (some? *cx*)
              ; NOTE: state changes in the inner template are not visible in the outside context
              ; NOTE: :bindings doesn't work here, upstream bug
              ; TODO: fork this new context before merging so we don't bind 'locals into the parent
              (let [new-cx (sci/merge-opts *cx* opts)]
                (with-meta new-cx {:flower/filename filename}))
              (create-sci-cx filename opts)) ]
     (binding [*cx* cx]
       (teval (parse-or-fatal parse src filename) src *cx*)))))
