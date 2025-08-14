(ns flower.eval
  (:use flower.internal.utils)
  (:require
   [flower.hiccup]
   [flower.reflect]
   [flower.utils]
   [clj-commons.digest]
   [hiccup.util]
   [clojure.repl :as repl]
   [instaparse.core :as insta]
   [babashka.fs :as fs]
   [sci.core :as sci])
  (:import
   [org.jsoup.nodes Document]
   (org.jsoup.select Nodes)))

; sandboxing

(def ^{:dynamic true :private true} *cx* "only for use by render-page" nil)

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

(declare render-file)

(defn sci-defaults []
  {
   ; :load-fn load-fn
   ; NOTE: dynamic vars are *not* bound, which means that
   ; e.g. `*html-mode*` will not see any changes in the guest.
   ; see https://clojurians.slack.com/archives/C015LCR9MHD/p1753046766042839?thread_ts=1753045763.706789&cid=C015LCR9MHD
   ; maybe we can figure out a way to find dynamic vars with `dir`?
   ; but that still doesn't help find all functions that use them…
   :namespaces {'hiccup2.core hiccup-core
                'hiccup.util (copy-ns 'hiccup.util) 
                'hiccup.compiler hiccup-compiler
                'instaparse.core (copy-ns 'instaparse.core) 
                ; repl/doc tries to call private functions
                'clojure.repl (copy-ns 'clojure.repl true)
                'flower.utils flower.utils/bindings
                ; TODO: can't bind `reflect/read-file` until we do dependency tracking elsewhere
                ; 'flower.reflect {'*watching*
                ;                  (sci/copy-var
                ;                    flower.reflect/*watching*
                ;                    (sci/create-ns 'flower.reflect))}
                'flower.reflect (assoc (copy-ns 'flower.reflect)
                                       'render-file render-file)
                'flower.internal {'pprint pprint}
                'clj-commons.digest (copy-ns 'clj-commons.digest)
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
     (with-meta cx {:filename filename}))))

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
    ; TODO: this double-prints "flower: error" inside a template
    (apply fatal
          (fmt "failed to eval ${default-file}:")
          ; TODO: this is useless for file-not-found errors
          (ex-message e)
          "\n"
          (map #(print-sci-frame % default-file) useful-frames))))

(defn try-sci
  [cx f]
  ; TODO: render tracebacks nicely
  ; TODO: give a better error message for native libs that use eval
  (try (f)
       (catch clojure.lang.ExceptionInfo e
         ; TODO: env variables suck lmao, do something else
         (if (System/getenv "FLOWER_HOST_TRACE")
           (throw e)
           (print-sci-trace e (-> cx meta :filename))))))

(defn parse-string
  [cx s]
  (try-sci cx #(sci/parse-string cx s)))

(defn eval-form
  "form eval. innermost function; use this instead of sci/eval-form directly."
  [cx form]
  (binding [flower.reflect/*dependencies* #{}
            *cx* cx]
    (sci/binding [sci/out *err*
                  sci/err *err*]
      ; TODO: we shouldn't bind locals when loading expressions ...
      (doseq [clj (fs/glob "expressions" "**.clj")]
        (let [f (str clj)
              ns (->> f fs/strip-ext fs/file-name (str "flower.expressions."))
              ; TODO: shouldn't be necessary: https://clojurians.slack.com/archives/C015LCR9MHD/p1754590244420059?thread_ts=1754589990.970939&cid=C015LCR9MHD
              lisp (str "(do (ns " ns ")" (slurp f) ")")
              fcx (with-meta cx {:filename f})]
          (sci/with-bindings {sci/ns (sci/create-ns (symbol ns))}
            ; TODO: this doesn't set :file :(
            (try-sci fcx #(sci/eval-string* fcx lisp)))))
        (sci/with-bindings {sci/ns userns}
          (try-sci cx #(sci/eval-form cx form))))))

(defn embed-custom [cx s f]
  (->> s (parse-string cx) f (eval-form cx))) 

(defn eval-string [cx s]
  (embed-custom cx s identity))

(defn ppeval "pretty print eval" [cx s]
  (embed-custom cx s embed))

(defn inline-body [cx s]
  (embed-custom cx s
                (fn [body] `(flower.reflect/render-file ~(str body) "<inline>" {}))))

(defn ->source [src node]
  (apply subs src (insta/span node)))

(defn inline-render
  ([cx src ident body] (inline-render cx src ident '[] body))
  ([cx src ident args body]
    (let
         [rendered (inline-body cx body)
          quoted-args (embed-custom cx (->source src args)
                                    (fn [args] `(quote ~args)))
          call `(~ident ~@(conj quoted-args rendered))]
      (eval-form cx (embed call)))))

; TODO: allow weird syntax in front of Ident (maybe Atom+ or something)
; https://clojure.org/reference/reader
; this is tricky because `#_id` needs to parse as [:Syntax "#_"], _ can't be associated with the ident
; NOTE: <> are valid clojure idents, but disallowed unless they are in parentheses. too easy to write `<a name=◊x>`.
; TODO: allow escaping ] and } in InlineRender
; TODO: don't actually need to disallow whitespace in Atom now that InlineRender handles Vec properly
; TODO: i don't think this handles nested InlineRender properly
(def parse
   (insta/parser
     "Start = (Text | Lisp)*
      Text = #'[^◊]+'
      Lisp = <'◊'> FlowerSyntax
      FlowerSyntax = (OuterIdent | InlineRender | OuterList)
      OuterList = ReaderSyntax* List
      OuterIdent = Ident
      InlineRender = Ident ( Vec )? <'{'> ( #'[^}◊]' | Lisp )* <'}'>

      ReaderSyntax = #\"[\\[\\;@^#`~']\"
      Ident = #'[a-zA-Z0-9*+!_\\'?=/.:-]+'
      Form = <#'\\s*'> (Atom | List | Vec) <#'\\s*'>
      List = <'('> Form* <')'>
      Vec = <'['> Form* <']'>
      Atom = #'[^()\\[\\] ]+' "))

(defn teval
  "tree eval"
  ([tree src] (teval tree src (create-sci-cx (-> src meta :filename))))
  ([tree src cx]
      (insta/transform {
        :Start str
        :Text identity
        :Lisp identity
        :Ident symbol
        :List #(identity %&)
        :Atom identity
        :Form identity
        :FlowerSyntax identity
        ; TODO: i think this is wrong when ReaderSyntax is present?
        :OuterList #(ppeval cx (->source src %))
        :OuterIdent #(->> % embed (eval-form cx))
        :InlineRender #(apply inline-render cx src %&)
      } tree)))

; TODO: needs to account for pages not in clojure
(defn render-file
  "Render content with local variables available"
  ; TODO: this causes nothing but problems, replace it with an options map
  ([src filename] (render-file src filename {}))
  ([src filename locals]
   ; TODO: also bind locals in `flower.locals`
   (let [cx (if (some? *cx*) *cx*
              (create-sci-cx filename {:bindings locals}))]
     (teval (parse src) src cx))))
