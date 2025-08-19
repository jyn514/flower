(ns flower.eval
  (:use flower.internal.utils)
  (:require
   [babashka.fs]
   [clj-commons.digest]
   [java-time.api]
   [clojure.repl :as repl]
   [clojure.string :as str]
   [flower.hiccup]
   [flower.reflect]
   [flower.utils]
   [hiccup.util]
   [instaparse.core :as insta]
   [sci.core :as sci])
  (:import
   [java.io StringReader BufferedReader]
   [org.jsoup.nodes Document]
   (org.jsoup.select Nodes)))

; sandboxing

(def ^{:dynamic true :private true} *cx* "only for use by render-page" nil)

(defn load-sci-file [file] 
  (set! flower.reflect/*dependencies* (conj flower.reflect/*dependencies* file))
  {:file file :source (slurp file)})

(defn load-fn
  "load user code on-demand"
  [{ns- :namespace}]
    (when (str/starts-with? (name ns-) "expressions.")
      (let [as-path (str/replace ns- "." "/")
            file (str as-path ".clj")]
        (load-sci-file file))))

; see sci/binding for how to allow overriding this
(def userns (sci/create-ns 'user))
(defn copy-ns
  ([ns] (copy-ns ns false))
  ([ns include-private]
   (let [binding (sci/create-ns ns)
         vars (if include-private
                ; TODO: figure out why this filters bb/fs to an empty map lmao
                (filter #(instance? clojure.lang.IDeref %) (ns-map ns))
                (ns-publics ns))
         ; copy-var* assumes that it can deref any var; make sure that's true
         bindings (update-vals vars
                               #(sci/copy-var* % binding))]
     (with-meta bindings {:ns binding}))))

(defn pprint [x]
  (cond (or (instance? sci.lang.Var x) (nil? x)) ""
        (hiccup.util/raw-string? x) (str x)
        (instance? Document x) (Document/.outerHtml x)
        (instance? Nodes x) (Nodes/.outerHtml x)
        (sequential? x) (apply str (map pprint x))
        :else (print-str x)))

(defn embed
  "given a quoted form, embeds it in a program that prints out the stringified value"
  [lisp]
  `(flower.eval/pprint ~lisp))

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
(def clojure-core
  (into {} (for [sym missing-core]
             [sym (sci/copy-var* (resolve sym) 'clojure.core)])))

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
   :namespaces {'hiccup2.core hiccup-core
                'hiccup.util (copy-ns 'hiccup.util) 
                'hiccup.compiler hiccup-compiler
                'instaparse.core (copy-ns 'instaparse.core) 
                'clojure.core clojure-core
                ; repl/doc tries to call private functions, so we need to copy those too
                'clojure.repl (copy-ns 'clojure.repl true)
                'flower.utils flower.utils/bindings
                'flower.reflect (assoc (copy-ns 'flower.reflect)
                                       'render-file render-file)
                'flower.eval {'pprint pprint}
                'clj-commons.digest (copy-ns 'clj-commons.digest)
                'java-time.api (copy-ns 'java-time.api)
                'nextjournal.markdown (copy-ns 'nextjournal.markdown)}
   :bindings {'html (sci/copy-var flower.hiccup/html-2 userns)
              'fmt (sci/copy-var fmt userns)
              'doc (sci/copy-var repl/doc userns)
              'dir (sci/copy-var repl/dir userns)
              'source (sci/copy-var repl/source userns)
              'md->html flower.utils/md->html}
   ; keep this in sync with `dynamic` in native.clj
   :classes {'java.lang.StringBuilder java.lang.StringBuilder
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
             'org.jsoup.parser.Parser org.jsoup.parser.Parser}})

(defn create-sci-cx
  "Create SCI context with standard library and local variables"
  ([filename] (create-sci-cx filename {}))
  ([filename opts]
   (let [cx (->> opts (merge-deep (sci-defaults)) sci/init)]
     (with-meta cx {:flower/filename filename}))))

; rendering

(defn span->start [span src]
  (if-let [[start _] span]
    (let [[line-zero line] (->> (subs src 0 start)
                                StringReader.
                                BufferedReader.
                                line-seq
                                enumerate
                                last)]
      [line-zero (dec (count line))])
    [0 0]))

(defn print-sci-frame [f default-file [start-line start-column]]
  (let [var (str (:ns f) "/" (or (:name f) "<top-level>"))
        file (or (:file f)
                 ; TODO: this only catches the clojure runtime,
                 ; not bound flower functions
                 (if (:sci/built-in f)
                   "<clojure-runtime>"
                   "<bound-host-function>")) 
        [relative-line relative-column] [(:line f) (:column f)]
        [line column] (if (and relative-line relative-column (= default-file file))
                        [(+ relative-line start-line)
                         ; TODO: columns are messed up
                         (if (= relative-line 1) (+ relative-column start-column) relative-column)]
                        [relative-line relative-column])
        span (cond
               (and line column) (str " " line ":" column)
               line (str " " line)
               :else "")]
    (fmt " [${var} ${file}${span}]\n")))

(defn print-sci-trace [e stacktrace dup]
  (let [useful? #(or (:name %) (:line %) (not= (:ns %) 'user))
        ; TODO: don't print out clojure.core/{let,fn} - those happen during name res and are never useful
        useful-frames (dedupe (filter useful? stacktrace))
        file (-> e ex-data :flower/filename)
        src (-> e ex-data :flower/source)
        ; TODO: this doesn't handle InlineRender; *something* is going wrong
        start (-> e ex-data :flower/span (span->start src))]
    (when (and dup (not (instance? clojure.lang.ExceptionInfo dup)))
      (-> dup type pr-str (str ": ") print))
    (apply print
      (ex-message e)
      "\n"
      (map #(print-sci-frame % file start) useful-frames))))

(defn print-stack-trace [e]
  (if-let [sci-ex (sci/stacktrace e)]
      ; skip the inner error, sci duplicates messages >:(
      (let [inner (some-> e ex-cause ex-message)
            dup (= inner (ex-message e))]
        (print-sci-trace e sci-ex (when dup (ex-cause e)))
        (when dup
          (-> e ex-cause ex-cause)))
      (do
        (if (instance? clojure.lang.ExceptionInfo e)
          (println (ex-message e))
          (println (str (pr-str (class e)) ":") (ex-message e)))
        ; (st/print-stack-trace e)
        (ex-cause e))))

(defn print-cause-trace [ex]
  (loop [e ex
         first-loop true]
    (when (not first-loop)
      (print " Caused by: "))
    (when-let [cause (print-stack-trace e)]
      (recur cause false))))

(defn try-sci
  [cx f]
  ; TODO: give a better error message for native libs that use eval
  (try (f)
       (catch clojure.lang.ExceptionInfo cause
         (let [file (-> cx meta :flower/filename)
               span (-> cx meta :flower/span)
               src (-> cx meta :flower/source)
               msg (fmt "failed to eval ${file}")
               old-info (or (ex-data cause) {})
               new-info (assoc old-info
                               :flower/filename file
                               :flower/span span
                               :flower/source src)
               new-cause (ex-info (ex-message cause) new-info (ex-cause cause))
               ex (ex-info msg {:flower/exit true
                                :flower/eval true} new-cause)]
           (throw ex)))))

(defn parse-string
  ([s] (parse-string *cx* s))
  ([cx s] (try-sci cx #(sci/parse-string cx s))))

(defn eval-form
  "form eval. innermost function; use this instead of sci/eval-form directly."
  ([src form] (eval-form *cx* src form))
  ([cx src form]
   (when (env "FLOWER_DEBUG_EVAL")
     (eprint "eval-form: ")
     (eprn form))
   (binding [flower.reflect/*dependencies* #{}]
     (let [cx (with-meta cx (merge (meta cx)
                                   {:flower/span (insta/span form)
                                    :flower/source src}))]
       (sci/binding [sci/out *err*
                     sci/err *err*
                     sci/ns userns
                     sci/file (-> cx meta :flower/filename)]
         (try-sci cx #(sci/eval-form cx form)))))))

(defn ->source [src node]
  (apply subs src (insta/span node)))

(defn inline-render
  ([cx src ident body] (apply inline-render cx src ident '[] body))
  ([cx src ident args body]
    (let [parsed-args (->> (->source src args) (parse-string cx))
          call `(~ident ~@(concat parsed-args body))]
      (embed call))))

; TODO: allow weird syntax in front of Ident (maybe Atom+ or something)
; https://clojure.org/reference/reader
; this is tricky because `#_id` needs to parse as [:Syntax "#_"], _ can't be associated with the ident
; NOTE: <> are valid clojure idents, but disallowed unless they are in parentheses. too easy to write `<a name=◊x>`.
; TODO: allow escaping ] and } in InlineRender
; TODO: don't actually need to disallow whitespace in Atom now that InlineRender handles Vec properly
; TODO: this renders nested InlineRender eagerly, it needs to delay evaluation
(def parse
   (insta/parser
     "Start = (Text | Lisp)*
      Text = #'[^◊]+'
      Lisp = <'◊'> FlowerSyntax
      FlowerSyntax = (OuterIdent | InlineRender | OuterList)
      OuterList = ReaderSyntax* List
      OuterIdent = Ident
      InlineRender = Ident ( Vec )? <'{'> NestedRender <'}'>
      NestedRender = ( #'[^}◊]+' | Lisp )*

      ReaderSyntax = #\"[\\[\\;@^#`~']\"
      Ident = #'[a-zA-Z0-9*+!_\\'?=/.:-]+'
      Form = <#'\\s*'> (Atom | List | Vec) <#'\\s*'>
      List = <'('> Form* <')'>
      Vec = <'['> Form* <']'>
      Atom = #'[^()\\[\\] ]+' "))

(defn- transformer
  "'''IR''' (really just fancy parse-form)"
  [tree src cx]
  (insta/transform
    {:Start vector
     :Text identity
     :Lisp identity
     :Ident symbol
     :List #(identity %&)
     :Atom identity
     :Form identity
     :FlowerSyntax identity
     ; TODO: i think this is wrong when ReaderSyntax is present?
     :OuterList #(->> (->source src %) (parse-string cx) embed)
     :OuterIdent #(->> % embed)
     :InlineRender #(apply inline-render cx src %&)
     :NestedRender #(identity `((str ~@%&)))
     } tree))

; you can see all fields with `(into {} err)`
(defn- render-parse-error [cx ex]
  (let [filename (-> cx meta :flower/filename)
        base (fmt "failed to parse ${filename}:\n" )
        lines (->> ex pr-str str/split-lines)
        indented (str/join "\n" (map #(str "  " %) lines))]
    (str base indented)))

(defn- on-parse-event [cx src ev]
  (if (string? ev) ev
    (eval-form cx src ev)))

(defn teval
  "tree eval"
  ([tree src cx]
   (let [events (transformer tree src cx)]
     (when (instance? instaparse.gll.Failure events)
      (fatal (render-parse-error cx events)))
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
   (binding [*cx* (if (some? *cx*)
                    ; TODO: this ignores `locals`
                    (with-meta *cx* {:flower/filename filename})
                    (create-sci-cx filename {:bindings locals}))]
     (teval (parse src) src *cx*))))

(defn create-fs-cx
  [filename]
  (let [override {:namespaces
                  ; TODO: sandboxing
                  {'babashka.fs (copy-ns 'babashka.fs)}}]
    (create-sci-cx filename override)))
