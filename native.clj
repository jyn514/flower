(ns native
  (:require
   [babashka.fs :as fs]
   [babashka.process :as ps]
   [babashka.process.pprint]
   [clojure.data.json :as json]
   [clojure.set :as set :refer [union]]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(defn eprintln [& args]
  (binding [*out* *err*] (apply println "native:" args)))

(defn strip-prefix
  [s pre]
  (let [quoted (java.util.regex.Pattern/quote pre)
        prefix (re-pattern (str "^" quoted))]
    (str/replace-first s prefix "")))

(defn symmetric-difference [A B]
  (union (set/difference A B) (set/difference B A)))

(def is-win (str/starts-with? (System/getProperty "os.name") "Windows"))
(def is-linux (= (System/getProperty "os.name") "Linux"))

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file "target/flower.jar")
(def exe (str "target/flower" (when is-win ".exe")))
; https://github.com/livereload/livereload-js/blob/v4.0.2/dist/livereload.min.js
; keep this in sync with watch.clj
(def live-reload "META-INF/resources/flower/watch/livereload-4.0.2/livereload.js")
; keep this in sync with eval.clj
(def parser "META-INF/resources/flower/eval/parser.ebnf")
(def defaults "META-INF/resources/flower/defaults")

(defn clean [_]
  (b/delete {:path class-dir})
  (b/delete {:path exe})
  (b/delete {:path jar-file}))

(def git-output
  (->> "git ls-tree -r --name-only HEAD defaults"
                 (ps/shell {:out :string}) :out))
(def manifest-path "MANIFEST.txt")
(def default-files (set (str/split git-output #"\n")))
(def all-files (set (map str (fs/glob "defaults" "**"))))
(when-let [diff (symmetric-difference default-files all-files)]
  (eprintln "warning: ignoring" (count diff) "untracked default files"))

(def manifest-contents
  (str/join "\n"
            (map #(strip-prefix % "defaults/")
                 default-files)))
(def defaults-target (str class-dir "/" defaults))

(defn all-public [& names]
  (for [t names]
    {:type t
     :allDeclaredConstructors true
     :allPublicConstructors true
     :allDeclaredFields true
     :allPublicFields true
     :allDeclaredMethods true
     :allPublicMethods true}))
; keep this in sync with :classes in flower.eval
(def all-dynamic
  (all-public
    "java.lang.AssertionError"
    "java.lang.Class"
    "java.lang.String"
    "java.lang.Integer"
    "java.util.List"
    "clojure.lang.PersistentVector"
    "org.jsoup.Jsoup"
    "org.jsoup.select.Elements"
    "org.jsoup.nodes.Node"
    "org.jsoup.nodes.Element"
    "org.jsoup.nodes.Comment"
    "org.jsoup.nodes.TextNode"
    "org.jsoup.nodes.Document"
    "org.jsoup.nodes.DocumentType"
    "org.jsoup.nodes.Attribute"
    "org.jsoup.nodes.Attributes"
    "org.jsoup.nodes.XmlDeclaration"
    "org.jsoup.parser.Parser"))
(def reachable
  {:reflection all-dynamic
   :resources
   [{:glob "META-INF/resources/flower/**"}
    {:glob "org/slf4j/impl/StaticLoggerBinder.class"}
    {:glob "simplelogger.properties"}]})

(defn manifest [_]
  (fs/write-bytes (str "defaults/" manifest-path) (.getBytes manifest-contents))
  (b/copy-file {:src (str "defaults/" manifest-path)
                :target (format "%s/%s/%s" class-dir defaults manifest-path)}))

(defn uberjar [dev]
  (let [assert (if dev "with" "without")]
    (eprintln "Build uberjar" jar-file assert "type assertions"))
  (clean nil)
  (manifest nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (doseq [f default-files]
    (b/copy-file {:src f
                  :target (str defaults-target "/" (strip-prefix f "defaults/"))}))
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :ns-compile '[flower.main]
                  :bindings {#'clojure.core/*assert* (not= false dev)}
                  ; JLine likes to bundle .dll files even on Linux. Tell it not to do that.
                  :java-opts ["-Djline.terminal.jna=false"]
                  :class-dir class-dir})
  (let [target (str class-dir "/META-INF/native-image/flower/main/reachability-metadata.json")
        serialized (json/write-str reachable)]
    (b/write-file {:path target :string serialized}))
  (b/copy-file {:src live-reload
                :target (str class-dir "/" live-reload)})
  (b/copy-file {:src parser
                :target (str class-dir "/" parser)})
  (b/copy-file {:src "scripts/run-jar.sh"
                :target "target/flower"})
  (b/uber {:class-dir class-dir
           :uber-file jar-file
           :basis basis
           :main 'flower.main}))

; https://github.com/babashka/babashka/blob/e2316f1bbef9daa9e5ec801a9bcbc0ece703d076/resources/META-INF/native-image/babashka/babashka/native-image.properties#L15

(def java-interop
  ["net.thisptr.jackson.jq"
   "org.yaml.snakeyaml"
   "org.commonmark"
   "org.jsoup"
   "org.nibor.autolink"
   "com.fasterxml.jackson"])

(defn args [dev]
  ["native-image" "-jar" jar-file exe
   "--silent"
   (when is-linux "--gc=G1")
   (if dev "-Ob" "-Os")
   "--no-fallback" "--exact-reachability-metadata"
   "--features=clj_easy.graal_build_time.InitClojureClasses"
   (str "--initialize-at-build-time=" (str/join "," java-interop))])

(defn graal [dev] (str/join " " (args dev)))

(defn -native-helper [dev]
  (uberjar dev)
  (eprintln "Build Graal Native executable")
  (println (graal dev))
  (ps/shell (graal dev))
  (let [size (-> exe fs/size (/ (* 1024 1024)) double)
        desc (if dev "dev" "release")]
    (eprintln "Built" exe (format "(%s %.2f MB)" desc size))))

(defn native [_] (-native-helper false))
(defn native-dev [_] (-native-helper true))
