; Portions copyright Masashi Iizuka under Eclipse Public License 2.0
; see https://github.com/liquidz/frontmatter

(ns flower.core
  (:use [flower.utils])
  (:import (net.thisptr.jackson.jq JsonQuery Scope Versions Output)
           (com.fasterxml.jackson.databind ObjectMapper JsonNode))
  ; net.thisptr.jackson.jq/Output
           ; (com.fasterxml.jackson.databind.node Array))
  (:require [instaparse.core :as insta]
            [sci.core :as sci]
            [babashka.fs :as fs]
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
            [jq.api :as jq]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))

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
        publics (ns-publics ns)]
    (update-vals publics #(sci/copy-var* % binding))))

(defn pprint [x]
  (cond (var? x) ""
        (hiccup.util/raw-string? x) (str x)
        (instance? org.jsoup.select.Nodes x) (.outerHtml x)
        :else (print-str x)))

(defn embed
  "given a quoted form, embeds it in a program that prints out the stringified value"
  [lisp]
      ; can't just use normal dequoting here. if there is a `(require)` that is used later in `lisp`,
    ; it won't be evaluated eagerly and we will get a resolution error from `let`.
    ; use `eval` to delay resolution.
    `(flower.internal/pprint (eval '~lisp)))
    ; (list 'flower.internal/pprint ('do lisp)))
    ; `(let [user-code# (eval '~lisp)] ; sci.lang.Var means this was a `def`
    ;     (cond (var? user-code#) ""
    ;           (hiccup.util/raw-string? user-code#) (str user-code#)
    ;           (instance? org.jsoup.select.Nodes user-code#) (.outerHtml user-code#)
    ;           true (print-str user-code#))))

(defn create-sci-cx
  "Create SCI context with standard library and local variables"
  ([] (create-sci-cx {}))
  ([opts]
    (sci/init (-> opts (merge-deep {
      ; :load-fn load-fn
      ; NOTE: dynamic vars are *not* bound, which means that e.g. `*html-mode*` will not see any changes in the guest.
      ; see https://clojurians.slack.com/archives/C015LCR9MHD/p1753046766042839?thread_ts=1753045763.706789&cid=C015LCR9MHD
      ; maybe we can figure out a way to find dynamic vars with `dir`? but that still doesn't help find all functions that use them…
      :namespaces {'hiccup2.core (copy-ns 'hiccup2.core) 
                   'hiccup.util (copy-ns 'hiccup.util) 
                   'hiccup.compiler (copy-ns 'hiccup.compiler) 
                   'instaparse.core (copy-ns 'instaparse.core) 
                   ; 'clojure.repl (copy-ns 'clojure.repl)
                   'flower.utils (copy-ns 'flower.utils)
                   'flower.select (copy-ns 'flower.select)
                   'flower.internal {'pprint pprint}
                   'nextjournal.markdown (copy-ns 'nextjournal.markdown)}
      :bindings {'html (sci/copy-var h/html userns)
                 'str str
                 'fmt (sci/copy-var fmt userns)
                 'markdown markdown}
      ; TODO: this has implications for Graal
      ; https://www.graalvm.org/latest/reference-manual/native-image/metadata/
      :classes {'java.lang.StringBuilder java.lang.StringBuilder}}) ))))

; rendering

(defn eval-form
  "form eval. innermost function; use this instead of sci/eval-form directly."
  [cx form]
  (sci/binding [sci/out *err*
                sci/err *err*]
    (sci/eval-form cx form)))

(defn seval "string eval" [cx s]
  (let [sread #(sci/parse-string cx %)]
    (->> s sread embed (eval-form cx))))

(def parse
   (insta/parser
     "Start = (Text | Lisp)*
      Text = #'[^◊]+'
      Lisp = <'◊'> (List | Ident)
      List = <'('> (Atom | List)* <')'>
      Ident = #'[a-zA-Z_][a-zA-Z0-9_-]*'
      Atom = #'[^()]*' "))

(defn teval
  ([tree src] (teval tree src (create-sci-cx)))
  ([tree src cx]
      (insta/transform {
        :Start str
        :Text identity
        ; str? if this was an Ident
        :Lisp #(if (string? %) %
                (seval cx (apply subs src (insta/span %))))
        :Ident #(seval cx %)
      } tree)))

(defn render
  "Render content with local variables available"
  ([src] (render src {}))
  ([src locals]
   (let [cx (create-sci-cx {:bindings locals})]
    (teval (parse src) src cx))))

; preprocessing

(defn render-page
  "Preprocess and render a JSON blob"
  [parsed]
  (let [rendered (render (:content parsed) {'frontmatter (:frontmatter parsed)})]
    {:content rendered
     :frontmatter (:frontmatter parsed)}))

; postprocessing
(defn postprocess
  "Given a `{:content x :frontmatter y :transformer z}` map,
   run the clojure in file `:transformer` on `{:content :frontmatter}`."
  [parsed transformer]
  (let [locals {'page parsed}
  ; (let [locals {'page {:content (:content parsed)
  ;                      :frontmatter (:frontmatter parsed)}}
        cx (create-sci-cx {:bindings locals #_:load-fn #_load})
        ; NOTE: parse-string only parses a single form, so we have to wrap the file in `do`
        f (slurp transformer)
        ls (str "(do " f ")")
        transformer (->> ls (sci/parse-string cx))
        lisp (embed (list 'do transformer '(transform page)))
        html (eval-form cx lisp)]
    (merge parsed {:content html})
    ))

; meta-build system

(defn create-fs-cx
  []
  (let [fs (copy-ns 'babashka.fs)
        build (copy-ns 'flower.build)]
    (create-sci-cx
      {:namespaces
       ; TODO: sandboxing
       {'babashka.fs fs
        'fs fs
        'flower.build build
        'build build}})))

(defn load-meta [dir]
  (map #(-> slurp build/split-frontmatter :frontmatter)
       (fs/glob dir "**.md")))

(defn configure
  "Run `build.clj` to generate a build.ninja and save the output to disk."
  [in out]
  (let [ninja-writer (new java.io.StringWriter)
        page-meta (load-meta "pages")
        template-meta (load-meta "templates")
        ; frontmatter {:pages page-meta :templates template-meta}
        frontmatter page-meta
        dst (fs/path out)]
    (binding [flower.build/*ninja* ninja-writer
              flower.build/*frontmatter* frontmatter]
      (let [
            cx (create-fs-cx)
            embedded (str "(do" (slurp in) ")")
            lisp (sci/parse-string cx embedded)
            ]
        (eval-form cx lisp)))
    (->> ninja-writer str .getBytes (fs/write-bytes dst))))

; template embedding
(defn embed-template
  "Given a {:content :frontmatter} page and the name of a template file,
  render `template` in context."
  [embed template-name]
  (let [{template-frontmatter :frontmatter
         ; TODO: layering violation, we shouldn't be reading this off disk.
         ; instead we should run `split-frontmatter` on the template too
         ; and then merge the two.
         template :content} (-> template-name slurp build/split-frontmatter)
        frontmatter (merge-deep template-frontmatter (:frontmatter embed))
        locals {'content (:content embed)
                'frontmatter frontmatter}
        embedded (render template locals)]
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
  ([data query] (jq data query false))
  ([data query raw]
   (let [res (jq/execute data query)]
     (if raw (json/read-str res) res))))

; IO

(defn map-json
  "Given a function `f` that transforms a clojure map to a clojure map,
   read the map as JSON from stdin and write it to stdout.
   If any `args` are present, they will be passed after the map."
  [f & args]
  ; TODO: https://clojure.atlassian.net/browse/DJSON-43
  (let [before (-> *in* (java.io.PushbackReader. 64)
                   (json/read :key-fn keyword))
        after (apply f before args)]
    (json/write after *out*)))

(defn -main [& args]
  (case (first args)
    ("configure") (configure "build.clj" "build.ninja")
    ("split-frontmatter") (-> *in* slurp build/split-frontmatter (json/write *out*))
    ("render-page") (map-json render-page)
    ("embed-template") (map-json embed-template (second args))
    ("postprocess") (map-json postprocess (second args))
    ("jq") (-> *in* slurp
               (jq (second args) (= (nth args 2 "") "-r"))
               println)
    (error (str "unrecognized command: " (first args)))))

;
; https://babashka.org/
; https://github.com/weavejester/hiccup
; for repl
(def src "x◊(+ 1 2)")
