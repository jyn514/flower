; Portions copyright Masashi Iizuka under Eclipse Public License 2.0
; see https://github.com/liquidz/frontmatter

; (ns blossom.core
(ns blossom
  (:require [instaparse.core :as insta]
            [sci.core :as sci]
            [hiccup2.core :as h]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.edn       :as edn]
            [yaml.core     :as yaml]
            [toml-clj.core :as toml]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))

; helpers

(defmacro fmt [^String string]
  (let [-re #"#\{(.*?)\}"
        fstr (clojure.string/replace string -re "%s")
        fargs (map #(read-string (second %)) (re-seq -re string))]
    `(format ~fstr ~@fargs)))

(defn error [msg] (binding [*out* *err*]
                    (println (str "blossom: error: " msg))))

; rendering

(defn load-sci-file [file] 
  {:file file :source (slurp file)})

(defn load-fn
  "load user code on-demand"
  [{ns- :namespace}]
    (when (str/starts-with? "flower.user." (name ns-))
      (let [file (-> ns- name (str/split #"\.") last (str "lib/" ".clj"))]
        (load-sci-file file))))

(defn copy-macro [sym] `(do ^:sci/macro (fn [_&form# _&env# & rest#] (~sym rest#))))
(defn copy-ns [ns]
  (let [binding (sci/create-ns ns)
        publics (ns-publics ns)]
    (update-vals publics #(sci/copy-var* % binding))))

; https://groups.google.com/g/clojure/c/UdFLYjLvNRs/m/8fd9fvNur6cJ
(defn merge-deep [& maps]
  (if (every? map? maps)
    (apply merge-with merge-deep maps)
    (last maps)))

(defn inspect [x] (println x) x)

(defn create-sci-cx
  "Create SCI context with standard library and local variables"
  ([] (create-sci-cx {}))
  ([opts]
    (sci/init (-> opts (merge-deep {
      :load-fn load-fn
      ; NOTE: dynamic vars are *not* bound, which means that e.g. `*html-mode*` will not see any changes in the guest.
      ; see https://clojurians.slack.com/archives/C015LCR9MHD/p1753046766042839?thread_ts=1753045763.706789&cid=C015LCR9MHD
      ; maybe we can figure out a way to find dynamic vars with `dir`? but that still doesn't help find all functions that use them…
      :namespaces {'hiccup2.core (copy-ns 'hiccup2.core) 
                   'hiccup.util (copy-ns 'hiccup.util) 
                   'hiccup.compiler (copy-ns 'hiccup.compiler) 
                   'instaparse.core (copy-ns 'instaparse.core) 
                   'nextjournal.markdown (copy-ns 'nextjournal.markdown)}
      :bindings {'html (copy-macro 'h/html)
                 'str str
                 'fmt (copy-macro 'fmt)}}) inspect))))

(defn embed
  "given a quoted form, embeds it in a program that prints out the stringified value"
  [lisp]
    ; (println lisp)
    `(let [user-code# ~lisp]
        ; sci.lang.Var means this was a `def`
        (cond (var? user-code#) ""
              (hiccup.util/raw-string? user-code#) (str user-code#)
              true (print-str user-code#))))

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
   (let [cx (create-sci-cx {:locals locals})]
    ; (println parsed src)
    (teval (parse src) src cx))))

; preprocessing

(defn render-page
  "Preprocess and render a JSON blob"
  [json] (let [parsed (json/read-str json)]
           ; (println parsed)
           (render (get parsed "content") (get parsed "frontmatter"))))

; frontmatter

; https://github.com/liquidz/frontmatter/blob/34a86ed3c6524f63cb457079c1316d9707be061a/src/frontmatter/core.clj
(defn- split-lines
  [lines delim]
  (let [x (take-while #(not= delim %) lines)]
    (list x (drop (+ 1 (count x)) lines))))

(defn- parse-json [s]
  (json/read-str (str "{" s "}")
                 :key-fn keyword))

(defn- parse-edn [s]
  (edn/read-string (str "{" s "}")))

(defn- select-parse-fn
  [first-line]
  (case first-line
    "---" yaml/parse-string
    "+++" toml/read-string
    ";;;" parse-json ; TODO: just use {} like hugo
    "###" parse-edn
    nil))

(defn parse-frontmatter
  [original-body]
  (let [[first-line & rest-lines] (str/split-lines original-body)
        [frontmatter body]        (split-lines rest-lines first-line)]
    (if-let [parser (select-parse-fn first-line)]
      {:content (str/join "\n" body)
       :frontmatter (parser (str/join "\n" frontmatter))}
      {:frontmatter {} :content original-body})))

; postprocessing
(defn postprocess
  [json]
  (let [parsed (json/read-str json :key-fn keyword)
        transformer (:transformer parsed)
        lisp (embed
             '(do;(ns transformer)
               ; (flower.__internal/load-file transformer)
               ; *ns*))
               ; (clojure.repl/dir transformer)))
               ; (println *ns*)
               (require 'flower.__internal.transformer)
               ; (println (all-ns))
               (clojure.repl/dir flower.__internal.transformer)
               ))
               ; (flower.__internal.transformer/transform page)))
        load (fn [{ns- :namespace}]
               (println ns-)
               (if (= ns- 'flower.__internal.transformer)
                 (load-sci-file transformer)
                 (load-fn {:namespace ns-})))
        locals {'transformer transformer
                'page {:content (:content parsed)
                       :frontmatter (:frontmatter parsed)}}
        ; ns- {'flower.__internal {'load-file load-file}}
        cx (create-sci-cx {:locals locals :load-fn load})]
    (println lisp transformer cx)
    (->> lisp (eval-form cx))))
           ; (println parsed)
           ; (render (:content parsed) (:frontmatter parsed))))

(defn -main [& args]
  (case (first args)
        ("render-page") (->> *in* slurp render-page print)
        ("postprocess") (->> *in* slurp postprocess print)
        ("split-frontmatter") (->> *in* slurp parse-frontmatter (json/write *out*))
        (error (str "unrecognized command: " (first args)))))

;
; (defn process-template
;   "Process a template file with page data"
;   [template-path page-data]
;   (let [template-content (slurp template-path)
;         {:keys [metadata content]} (parse-frontmatter template-content)]
;     (render content {:page page-data})))

; (defn collect-pages
;   "Collect all pages for index generation"
;   [src-dir]
;   (->> (file-seq (io/file src-dir))
;        (filter #(.isFile %))
;        (filter #(or (str/ends-with? (.getName %) ".md")
;                     (str/ends-with? (.getName %) ".html.clj")))
;        (map (fn [file]
;               (let [content (slurp file)
;                     {:keys [metadata]} (parse-frontmatter content)]
;                 (merge metadata {:path (.getPath file)
;                                 :title (or (:title metadata) 
;                                           (str/replace (.getName file) #"\.(md|html\.clj)$" ""))}))))))
;
; (defn process-index-page
;   "Process an index page with access to all pages"
;   [page-path all-pages]
;   (let [content (slurp page-path)
;         {:keys [metadata content]} (parse-frontmatter content)
;         processed-content (render content {:pages all-pages})
;         
;         template-name (or (:template metadata) "index.html.clj")
;         template-path (str "lib/" template-name)]
;     
;     (if (.exists (io/file template-path))
;       (process-template template-path 
;                        {:metadata metadata 
;                         :content processed-content
;                         :path page-path
;                         :pages all-pages})
;       processed-content)))
;
; (defn process-markdown
;   "Process markdown content using nextjournal/markdown"
;   [content]
;   (-> content
;       md/parse
;       (md.transform/->hiccup)
;       h/html
;       str))

; Test data and compatibility
(def src "x◊(+ 1 2)")

; Conditional execution for command line
; (when *command-line-args*
;   (println *command-line-args* )
;   (-main))
  ; (if (= (first *command-line-args*) "build")
  ;   (apply -main-build (rest *command-line-args*))
  ;   (apply renderf *command-line-args*)))

; ; https://babashka.org/
; ; https://github.com/weavejester/hiccup
; ; for repl
(def src "x◊(+ 1 2)")

; Tests
; (ns blossom-test 
;   (:require [clojure.test :as t]
;             [clojure.string :as str]
;             [blossom]))
;
; (t/deftest parser
;   (t/testing "accepts valid"
;     (t/is (= "x3" (blossom/render blossom/src))))
;   (t/testing "any start"
;     (t/is (= "3x" (blossom/render "◊(+ 1 2)x"))))
;   (t/testing "ident shortcut"
;     (t/is (= "x3" (blossom/render "x◊test" {'test 3}))))
;   (t/testing "errors handled gracefully"
;     (t/is (str/includes? (blossom/render "◊(") "Parse error"))))
;
; (t/deftest frontmatter-parsing
;   (t/testing "parses frontmatter"
;     (let [content "---\ntitle = Hello World\ntemplate = custom.html.clj\n---\n# Hello\n\nContent here"
;           result (blossom/parse-frontmatter content)]
;       (t/is (= "Hello World" (get-in result [:metadata :title])))
;       (t/is (= "custom.html.clj" (get-in result [:metadata :template])))
;       (t/is (= "# Hello\n\nContent here" (:content result)))))
;   
;   (t/testing "handles missing frontmatter"
;     (let [content "# Hello\n\nContent here"
;           result (blossom/parse-frontmatter content)]
;       (t/is (= {} (:metadata result)))
;       (t/is (= content (:content result))))))
;
; (t/deftest markdown-processing
;   (t/testing "processes markdown correctly"
;     (let [md-content "# Hello\n\nThis is **bold** text."
;           result (blossom/process-markdown md-content)]
;       (t/is (str/includes? result "<h1>"))
;       (t/is (str/includes? result "<strong>bold</strong>")))))
;
; (ns blossom-test (:require [clojure.test :as t])
;   (:require [blossom]))
; ; (defmacro desc t/testing)
; (t/deftest parser
;   (t/testing "accepts valid"
;     (t/is (= "x3" (blossom/render blossom/src))))
;   (t/testing "any start"
;     (t/is (= "3x" (blossom/render "◊(+ 1 2)x"))))
;   (t/testing "errors"
;     (t/is (= instaparse.gll.Failure (type (blossom/render "◊("))))))
; (t/deftest eval-values
;   (t/testing "lazy collections ok"
;     (t/is (= "(2 3 4)" (blossom/render "◊(map inc [1 2 3])")))))
;
; ; (t/run-tests)
