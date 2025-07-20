(ns blossom
  (:require [instaparse.core :as insta]
            [sci.core :as sci]
            [hiccup2.core :as h]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))

(defmacro fmt [^String string]
  (let [-re #"#\{(.*?)\}"
        fstr (clojure.string/replace string -re "%s")
        fargs (map #(read-string (second %)) (re-seq -re string))]
    `(format ~fstr ~@fargs)))

(defn create-sci-context
  "Create SCI context with standard library and local variables"
  [locals]
  ; todo: https://github.com/babashka/sci/tree/master?tab=readme-ov-file#macros
  (def html ^:sci/macro (fn [_&form _&env x & rest] (h/html (into [x] rest))))
  ; (defmacro copy-ns [sym ns]
  ;   `(let [binding (sci/create-ns ~sym)
  ;          new-ns (sci/copy-ns ~ns binding)]
  ;           new-ns))
  ; (copy-ns 'clojure.string 'clojure.string)
  ; (println "hiiiii")
  (defn copy-ns [ns]
    (let [binding (sci/create-ns ns)
          publics (ns-publics ns)]
      (update-vals publics #(sci/copy-var* % binding))))
  (sci/init {:namespaces
             ; {}
              {'hiccup2.core (copy-ns 'hiccup2.core)
               'clojure.lang {'Var clojure.lang.Var}}
              ; {'hiccup2.core {'html html}]
               ; 'clojure.string (copy-ns 'clojure.string 'clojure.string)}
             :bindings
              (merge {'html html
                      'str str
                      'fmt format} ; TODO: wrong
                      locals)}))

(def parse
   (insta/parser
     "Start = (Text | Lisp)*
      Text = #'[^◊]+'
      Lisp = <'◊'> (List | Ident)
      List = <'('> (Atom | List)* <')'>
      Ident = #'[a-zA-Z_][a-zA-Z0-9_-]*'
      Atom = #'[^()]*' "))

(defn teval
  ([tree src] (teval tree src (create-sci-context {})))
  ([tree src cx]
    (let [template
          ; sci.lang.Var means this was a `def`
          (fn [lisp] (fmt "(let [user-code #{lisp}] (str (if (= (type user-code) sci.lang.Var) \"\" (print-str user-code))))"))
          seval #(sci/eval-string* cx (template %))]
      (insta/transform {
        :Start str
        :Text identity
        ; str? if this was an Ident
        :Lisp #(if (string? %) % (seval (apply subs src (insta/span %))))
        :Ident #(seval %)
      } tree))))

(def frontmatter-regex #"(?s)^---\n(.*?)\n---\n(.*)$")

(defn parse-frontmatter
  "Parse YAML-like frontmatter from a string"
  [content]
  (if-let [[_ frontmatter body] (re-matches frontmatter-regex content)]
    (let [metadata (try
                     (->> frontmatter
                          str/split-lines
                          (map #(str/split % #"\s*=\s*" 2))
                          (filter #(= 2 (count %)))
                          (map (fn [[k v]] [(keyword k) (str/trim v)]))
                          (into {}))
                     (catch Exception _ {}))]
      {:metadata metadata :content body})
    {:metadata {} :content content}))

(defn render
  "Render content with local variables available"
  ([src] (render src {}))
  ([src locals]
   (let [parsed (parse src) cx (create-sci-context locals)]
     (if (insta/failure? parsed)
       (str "Parse error: " (pr-str parsed))
       (teval parsed src cx)))))

(defn load-library-files
  "Load all .clj files from lib/ directory"
  [lib-dir]
  (let [lib-files (->> (file-seq (io/file lib-dir))
                       (filter #(.isFile %))
                       (filter #(str/ends-with? (.getName %) ".clj"))
                       (filter #(not (str/ends-with? (.getName %) ".html.clj"))))]
    (reduce (fn [ctx file]
              (try
                (let [content (slurp file)]
                  (sci/eval-string content {:bindings (:bindings ctx)}))
                ctx
                (catch Exception e
                  (println "Error loading library file" (.getPath file) ":" (.getMessage e))
                  ctx)))
            (create-sci-context {})
            lib-files)))

(defn process-template
  "Process a template file with page data"
  [template-path page-data]
  (let [template-content (slurp template-path)
        {:keys [metadata content]} (parse-frontmatter template-content)]
    (render content {:page page-data})))

(defn process-page
  "Process a page file"
  [page-path]
  (let [content (slurp page-path)
        {:keys [metadata content]} (parse-frontmatter content)
        processed-content (render content)
        
        ; Apply template if specified
        template-name (or (:template metadata) "page.html.clj")
        template-path (str "lib/" template-name)]
    
    (if (.exists (io/file template-path))
      (process-template template-path 
                       {:metadata metadata 
                        :content processed-content
                        :path page-path})
      processed-content)))

(defn collect-pages
  "Collect all pages for index generation"
  [src-dir]
  (->> (file-seq (io/file src-dir))
       (filter #(.isFile %))
       (filter #(or (str/ends-with? (.getName %) ".md")
                    (str/ends-with? (.getName %) ".html.clj")))
       (map (fn [file]
              (let [content (slurp file)
                    {:keys [metadata]} (parse-frontmatter content)]
                (merge metadata {:path (.getPath file)
                                :title (or (:title metadata) 
                                          (str/replace (.getName file) #"\.(md|html\.clj)$" ""))}))))))

(defn process-index-page
  "Process an index page with access to all pages"
  [page-path all-pages]
  (let [content (slurp page-path)
        {:keys [metadata content]} (parse-frontmatter content)
        processed-content (render content {:pages all-pages})
        
        template-name (or (:template metadata) "index.html.clj")
        template-path (str "lib/" template-name)]
    
    (if (.exists (io/file template-path))
      (process-template template-path 
                       {:metadata metadata 
                        :content processed-content
                        :path page-path
                        :pages all-pages})
      processed-content)))

(defn process-markdown
  "Process markdown content using nextjournal/markdown"
  [content]
  (-> content
      md/parse
      (md.transform/->hiccup)
      h/html
      str))

; Legacy render function for compatibility
; (defn render [src] (render-with-locals src))

(defn renderf [in out]
  (spit out (render (slurp in))))

; (def -main (render (slurp *in*)))

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
(ns blossom-test 
  (:require [clojure.test :as t]
            [clojure.string :as str]
            [blossom]))

(t/deftest parser
  (t/testing "accepts valid"
    (t/is (= "x3" (blossom/render blossom/src))))
  (t/testing "any start"
    (t/is (= "3x" (blossom/render "◊(+ 1 2)x"))))
  (t/testing "ident shortcut"
    (t/is (= "x3" (blossom/render "x◊test" {'test 3}))))
  (t/testing "errors handled gracefully"
    (t/is (str/includes? (blossom/render "◊(") "Parse error"))))

(t/deftest frontmatter-parsing
  (t/testing "parses frontmatter"
    (let [content "---\ntitle = Hello World\ntemplate = custom.html.clj\n---\n# Hello\n\nContent here"
          result (blossom/parse-frontmatter content)]
      (t/is (= "Hello World" (get-in result [:metadata :title])))
      (t/is (= "custom.html.clj" (get-in result [:metadata :template])))
      (t/is (= "# Hello\n\nContent here" (:content result)))))
  
  (t/testing "handles missing frontmatter"
    (let [content "# Hello\n\nContent here"
          result (blossom/parse-frontmatter content)]
      (t/is (= {} (:metadata result)))
      (t/is (= content (:content result))))))

(t/deftest markdown-processing
  (t/testing "processes markdown correctly"
    (let [md-content "# Hello\n\nThis is **bold** text."
          result (blossom/process-markdown md-content)]
      (t/is (str/includes? result "<h1>"))
      (t/is (str/includes? result "<strong>bold</strong>")))))

(ns blossom-test (:require [clojure.test :as t])
  (:require [blossom]))
; (defmacro desc t/testing)
(t/deftest parser
  (t/testing "accepts valid"
    (t/is (= "x3" (blossom/render blossom/src))))
  (t/testing "any start"
    (t/is (= "3x" (blossom/render "◊(+ 1 2)x"))))
  (t/testing "errors"
    (t/is (= instaparse.gll.Failure (type (blossom/render "◊("))))))
(t/deftest eval-values
  (t/testing "lazy collections ok"
    (t/is (= "(2 3 4)" (blossom/render "◊(map inc [1 2 3])")))))

; (t/run-tests)
