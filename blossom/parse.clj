(ns blossom
  (:require [instaparse.core :as insta]
            [sci.core :as sci]
            [hiccup2.core :as h]
            [clojure.string :as str]
            [clojure.java.io :as io]
            ; [clojure.edn :as edn]
            ; [clojure.walk :as walk]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))

(def parse
   (insta/parser
     "Start = (Text | Lisp)*
      Text = #'[^◊]+'
      Lisp = <'◊'> (List | Ident)
      List = <'('> (Atom | List)* <')'>
      Ident = #'[a-zA-Z_][a-zA-Z0-9_-]*'
      Atom = #'[^()]*' "))

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

(defn classify-file
  "Classify file by type based on path and extension"
  [path]
  (cond
    (str/starts-with? path "src/")
    (cond
      (str/ends-with? path ".md.clj") :preprocessed-md
      (str/ends-with? path ".html.clj") :preprocessed-html
      (str/ends-with? path ".md") :page
      :else :static)
    
    (str/starts-with? path "lib/")
    (cond
      (str/ends-with? path ".html.clj") :template
      (str/ends-with? path ".clj") :library
      :else :static)
    
    :else :static))

(defn file-dependencies
  "Calculate dependencies for a file based on its type and metadata"
  [file-path file-type metadata]
  (case file-type
    :page [(or (:template metadata) "page.html.clj")]
    :template []
    :preprocessed-md []
    :preprocessed-html []
    :library []
    :static []))

(defn create-sci-context
  "Create SCI context with standard library and local variables"
  [locals]
  ; todo: https://github.com/babashka/sci/tree/master?tab=readme-ov-file#macros
  (def html ^:sci/macro (fn [_&form _&env x & rest] (h/html (into [x] rest))))
  (sci/init {:namespaces {'hiccup2.core {'html html}
                          'clojure.string str}
             :bindings (merge {'html html
                              'str str
                              'fmt format} ; TODO: wrong
                             locals)}))

(defn teval [tree src locals]
  (let [ctx (create-sci-context locals)]
    (insta/transform {
      :Start str
      :Text identity
      :Lisp (fn [l]
              (let [lisp (apply subs src (insta/span l))]
                (try
                  (sci/eval-string (str "(print-str " lisp ")") {:bindings (merge (:bindings ctx) locals)})
                  (catch Exception e
                    (str "Error: " (.getMessage e))))))
      :Ident (fn [ident]
               (try
                 (sci/eval-string (str "(print-str " ident ")") {:bindings (merge (:bindings ctx) locals)})
                 (catch Exception e
                   (str "Error: " (.getMessage e)))))
    } tree)))

(defn render-with-locals
  "Render content with local variables available"
  ([src] (render-with-locals src {}))
  ([src locals] 
   (let [parsed (parse src)]
     (if (insta/failure? parsed)
       (str "Parse error: " (pr-str parsed))
       (teval parsed src locals)))))

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
    (render-with-locals content {:page page-data})))

(defn process-page
  "Process a page file"
  [page-path]
  (let [content (slurp page-path)
        {:keys [metadata content]} (parse-frontmatter content)
        processed-content (render-with-locals content)
        
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
        processed-content (render-with-locals content {:pages all-pages})
        
        template-name (or (:template metadata) "index.html.clj")
        template-path (str "lib/" template-name)]
    
    (if (.exists (io/file template-path))
      (process-template template-path 
                       {:metadata metadata 
                        :content processed-content
                        :path page-path
                        :pages all-pages})
      processed-content)))

(defn build-dependency-graph
  "Build dependency graph for all files"
  [root-dir]
  (let [all-files (->> (file-seq (io/file root-dir))
                       (filter #(.isFile %))
                       (map #(.getPath %)))]
    (->> all-files
         (map (fn [path]
                (let [file-type (classify-file path)
                      {:keys [metadata]} (when (#{:page :template :preprocessed-md :preprocessed-html} file-type)
                                           (parse-frontmatter (slurp path)))]
                  {:path path
                   :type file-type
                   :metadata metadata
                   :dependencies (file-dependencies path file-type metadata)})))
         (group-by :type))))

(defn process-markdown
  "Process markdown content using nextjournal/markdown"
  [content]
  (-> content
      md/parse
      (md.transform/->hiccup)
      h/html
      str))

(defn flower-build
  "Main build function for flower SSG"
  [config]
  (let [{:keys [src-dir lib-dir output-dir]} config
        dependency-graph (build-dependency-graph ".")
        all-pages (collect-pages src-dir)]
    
    ; Phase 1: Build dependency graph (already done above)
    (println "Phase 1: Building dependency graph...")
    
    ; Phase 2: Preprocessing and template embedding
    (println "Phase 2: Processing files...")
    (doseq [page-file (:page dependency-graph)]
      (let [page-path (:path page-file)
            output-path (str/replace page-path #"^src/" output-dir)
            output-path (str/replace output-path #"\.md$" ".html")]
        (println "Processing page:" page-path)
        (let [processed (if (get-in page-file [:metadata :index])
                          (process-index-page page-path all-pages)
                          (process-page page-path))
              final-content (if (str/ends-with? page-path ".md")
                              (process-markdown processed)
                              processed)]
          (io/make-parents output-path)
          (spit output-path final-content))))
    
    ; Phase 3: Post-processing (placeholder)
    (println "Phase 3: Post-processing...")
    (println "Build complete!")))

; Legacy render function for compatibility
(defn render [src] (render-with-locals src))

(defn renderf [in out]
  (spit out (render (slurp in))))

(def -main renderf)

; Main entry point for building a flower site
(defn -main-build [& args]
  (let [config {:src-dir "src/"
                :lib-dir "lib/"
                :output-dir "public/"}]
    (flower-build config)))

; Test data and compatibility
(def src "x◊(+ 1 2)")

; Conditional execution for command line
(when *command-line-args*
  (println *command-line-args* )
  (if (= (first *command-line-args*) "build")
    (apply -main-build (rest *command-line-args*))
    (apply renderf *command-line-args*)))

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
    (t/is (= "x3" (blossom/render-with-locals "x◊test" {'test 3}))))
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

(t/deftest file-classification
  (t/testing "classifies files correctly"
    (t/is (= :page (blossom/classify-file "src/hello.md")))
    (t/is (= :template (blossom/classify-file "lib/page.html.clj")))
    (t/is (= :preprocessed-md (blossom/classify-file "src/about.md.clj")))
    (t/is (= :library (blossom/classify-file "lib/macros.clj")))
    (t/is (= :static (blossom/classify-file "public/style.css")))))

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
