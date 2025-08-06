(ns flower.eval
  (:use flower.internal.utils)
  (:import
    (org.jsoup.select Nodes))
  (:require
    [clojure.repl :as repl]
    [instaparse.core :as insta]
    [sci.core :as sci]
    [hiccup.util]
    [flower.hiccup]
    [flower.utils]
    ))

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
                   'clojure.repl (copy-ns 'clojure.repl true)  ; repl/doc tries to call private functions
                   'flower.utils flower.utils/bindings
                   'flower.select (copy-ns 'flower.select)
                   'flower.internal {'pprint pprint}
                   'nextjournal.markdown (copy-ns 'nextjournal.markdown)}
      :bindings {'html (sci/copy-var flower.hiccup/html-2 userns)
                 'fmt (sci/copy-var fmt userns)
                 'doc (sci/copy-var repl/doc userns)
                 'dir (sci/copy-var repl/dir userns)
                 'source (sci/copy-var repl/source userns)
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
    (apply fatal
          "failed to run interpreted clojure:"
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

(defn eval-form
  "form eval. innermost function; use this instead of sci/eval-form directly."
  [cx form]
  (sci/binding [sci/out *err*
                sci/err *err*]
    (try-sci cx #(sci/eval-form cx form))))

(defn parse-string
  [cx s]
  (try-sci cx #(sci/parse-string cx s)))

(defn seval "string eval" [cx s]
  (->> s (parse-string cx) embed (eval-form cx)))

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
