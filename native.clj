(ns native
  (:require
   [babashka.fs :as fs]
   [babashka.process :as ps]
   [babashka.process.pprint]
   [clojure.data.json :as json]
   [clojure.set :as set :refer [difference union]]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   ; HACK: be careful about recursive dependencies
   ; TODO: separate out base-utils from utils so we don't have to duplicate all these functions
   [expressions.ninja :as ninja]))

(defmacro fmt
  "Format string mini-language.
   Allows using `${var}` in a format string to refer to a variable in scope."
  [^String string]
  (let [-re #"\$\{(.*?)\}"
        fstr (str/replace string -re "%s")
        fargs (map #(read-string (second %)) (re-seq -re string))]
    `(format ~fstr ~@fargs)))

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
(defn flower-resource [path] (str "META-INF/resources/flower/" path))
(def live-reload (flower-resource "watch/livereload-4.0.2/livereload.js"))
; keep this in sync with eval.clj
(def parser (flower-resource "eval/parser.ebnf"))
(def defaults-dir (flower-resource "defaults"))
(def git-hash (flower-resource "git-hash"))

(def GIT-HASH (->> "git describe --always" (ps/shell {:out :string}) :out str/trimr))

(defn clean [_]
  (b/delete {:path class-dir})
  (b/delete {:path exe})
  (b/delete {:path jar-file}))

(defn parse-git [cmd]
  (-> (ps/shell {:out :string} cmd)
      :out (str/split #"\n") set))

(def manifest-path "MANIFEST.txt")
(def tracked-files
  (parse-git "git ls-tree -r --name-only HEAD defaults"))
(def ignored-files
  (parse-git "git ls-files --others --ignored --exclude-standard defaults"))
(def all-files (difference (set (remove fs/directory?
                                        (map str (fs/glob "defaults" "**"))))
                           ignored-files))

(defn default-files [include-untracked]
  (if include-untracked all-files
    (do
      (when-let [diff (symmetric-difference tracked-files all-files)]
        (eprintln "warning: ignoring" (count diff) "untracked default files")
        (prn diff))
      tracked-files)))

(defn manifest-contents [default-files]
  (str/join "\n"
            (map #(strip-prefix % "defaults/")
                 default-files)))
(def defaults-target (str class-dir "/" defaults-dir))

(defn all-public [& names]
  (for [t names]
    {:type t
     :allPublicConstructors true
     :allPublicFields true
     :allPublicMethods true}))
; keep this in sync with :classes in flower.eval
(def all-dynamic
  (all-public
    "java.lang.AssertionError"
    "java.lang.Class"
    "java.lang.String"
    "java.lang.Integer"
    "java.util.List"
    "java.util.regex.Pattern"
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

(defn manifest [{:keys [include-untracked]}]
  (spit (str "defaults/" manifest-path) (manifest-contents (default-files include-untracked)))
  (b/copy-file {:src (str "defaults/" manifest-path)
                :target (format "%s/%s/%s" class-dir defaults-dir manifest-path)}))

(defn uberjar [{:keys [dev include-untracked] :as opts}]
  (let [assert (if dev "with" "without")]
    (eprintln "Build uberjar" jar-file assert "type assertions"))
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (let [defaults (default-files include-untracked)]
    (manifest opts)
    (doseq [f defaults]
      (b/copy-file {:src f
                    :target (str defaults-target "/" (strip-prefix f "defaults/"))})))
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
  (spit (str class-dir "/" git-hash) GIT-HASH)
  ; TODO: on macOS this doesn't update the modified time, which causes ninja to unconditionally rebuild
  (b/copy-file {:src "scripts/run-jar.sh"
                :target "target/flower"})
  (b/uber {:class-dir class-dir
           :uber-file jar-file
           :basis basis
           :main 'flower.main}))

; https://github.com/babashka/babashka/blob/e2316f1bbef9daa9e5ec801a9bcbc0ece703d076/resources/META-INF/native-image/babashka/babashka/native-image.properties#L15

(def java-interop
  ["org.yaml.snakeyaml"
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

(defn -native-helper [{:keys [dev] :as opts}]
  (uberjar opts)
  (eprintln "Build Graal Native executable")
  (println (graal dev))
  (ps/shell (graal dev))
  (let [size (-> exe fs/size (/ (* 1024 1024)) double)
        desc (if dev "dev" "release")]
    (eprintln "Built" exe (format "(%s %.2f MB)" desc size))))

(defn native [opts] (-native-helper (assoc opts :dev false)))
(defn native-dev [opts] (-native-helper (assoc opts :dev true)))

; meta-build plan

(def flower-cli "target/flower")
(def all-defaults
  ; MANIFEST.txt gets rebuilt when we rebuild flower.
  ; avoid it always showing up as dirty.
  (remove #{(fs/path "defaults/MANIFEST.txt")}
          (fs/glob "defaults" "**")))

(defn plan [{:keys [build-cmd] :or {build-cmd "uberjar"}}]
  {:phony [{:name "flower-bin" :depends flower-cli}]
   :rules
   [{:name "ninja-meta"
     :command (fmt "clojure -T:build gen-plan :build-cmd ${build-cmd}")
     :generator true
     :description "rebuild meta-build.ninja"}
    {:name "flower-defaults"
     :restat true
     :command (fmt "cd defaults && ../${flower-cli} configure")
     :description "rebuild default build.ninja"}
    {:name "flower-bin"
     :command (fmt "clojure -T:build ${build-cmd} :include-untracked true")
     :description (fmt "rebuild flower itself (${build-cmd})")}]
   :builds
   [{:rule "ninja-meta"
     :outputs "build.ninja"
     :inputs "native.clj"}
    {:rule "flower-defaults"
     :outputs "defaults/build.ninja"
     :inputs "defaults/build.clj"}
    {:rule "flower-bin"
     :outputs flower-cli
     :inputs (concat (fs/glob "flower" "**") all-defaults
                     ["native.clj" "flower" "deps.edn"])}]})

(defn gen-plan [build-cmd]
  (->> build-cmd plan ninja/generate (spit "build.ninja")))
