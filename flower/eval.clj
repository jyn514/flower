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
                'flower.eval {'pprint pprint}
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
          (let [final `(do (~'ns ~'user) ~form)]
            (eprn final)
            (try-sci cx #(sci/eval-form cx final)))))))

; (defn eval-form
;   "Evaluate a quoted form as if it had been loaded with `load-file`."
;   [cx form]
;   ; can't just use normal dequoting here. if there is a `(require)` that is used later,
;   ; it won't be evaluated eagerly and we will get a resolution error.
;   ; use `eval` to delay resolution.
;   ; this has to be at the outermost level because eval doesn't see local bindings
;   ; (e.g. from let, for)
;   (eval-inner-form cx `(flower.eval/pprint (eval '~form))))
; (defn embed-custom [cx s f]
;   (f (parse-string cx s)) 

; (defn eval-string [cx s]
;   (eval-form cx (parse-string cx s)))
;
; (defn ppeval "pretty print eval" [cx s]
;   (->> s (parse-string cx) embed (eval-form cx)))

; (defn inline-body [form]
;   `(flower.reflect/render-file ~form "<inline>" {}))

(defn ->source [src node]
  (apply subs src (insta/span node)))

(defn inline-render
  ([cx src ident body] (apply inline-render cx src ident '[] body))
  ([cx src ident args body]
    ; (eprn body)
    (let [;inline-body #(identity `(str ~@%))
          ; rendered (inline-body (map #(parse-string cx %) body))
          ; rendered (inline-body body)
          quote-args #(identity `(quote ~@%))
          parsed-args (->> (->source src args) (parse-string cx))
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


(def x "")
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
     ; :NestedRender vector
     :NestedRender #(identity `((str ~@%&)))
     } tree))

(defn teval
  "tree eval"
  ([tree src] (teval tree src (create-sci-cx (-> src meta :filename))))
  ([tree src cx]
   (let [form (transformer tree src cx)
         ; TODO: maybe wrong? do we need to wrap this in pprint/eval?
         strs (map #(if (string? %) % (eval-form cx %)) form)]
     (apply str strs))))

; (defn eval-string [s]

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

(defn create-fs-cx
  [filename]
  (let [fs (copy-ns 'babashka.fs)
        build (copy-ns 'flower.build)]
    (create-sci-cx filename
      {:namespaces
       ; TODO: sandboxing
       {'babashka.fs fs
        'flower.build build}})))
