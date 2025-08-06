(ns native
  (:use [flower.internal.utils])
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.process :as ps]
            [babashka.process.pprint]))

(def is-win (str/starts-with? (System/getProperty "os.name") "Windows"))
(def is-linux (= (System/getProperty "os.name") "Linux"))

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file "target/flower.jar")
(def exe (str "target/flower" (when is-win ".exe")))
(def reachable "reachability-metadata.json")
; https://github.com/livereload/livereload-js/blob/v4.0.2/dist/livereload.min.js
; keep this in sync with live-reload.clj
(def live-reload "META-INF/resources/flower/live-reload/livereload-4.0.2/livereload.js")
(def defaults "META-INF/resources/flower/defaults")

(defn clean [_]
  (b/delete {:path class-dir})
  (b/delete {:path exe})
  (b/delete {:path jar-file}))

(def git-output
  (->> "git ls-tree -r --name-only HEAD defaults"
                 (ps/shell {:out :string}) :out))
(def manifest-path "MANIFEST.txt")
(def default-files (str/split git-output #"\n"))
(def manifest
  (str/join "\n"
            (map #(strip-prefix % "defaults/")
                 default-files)))
(def defaults-target (str class-dir "/" defaults))

(defn uberjar [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (doseq [f default-files]
    (b/copy-file {:src f
                  :target (str defaults-target "/" (strip-prefix f "defaults/"))}))
  (fs/write-bytes (str "defaults/" manifest-path) (.getBytes manifest))
  (b/copy-file {:src (str "defaults/" manifest-path)
                :target (format "%s/%s/%s" class-dir defaults manifest-path)})

  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :ns-compile '[flower.main]
                  :class-dir class-dir})
  (b/copy-file {:src (str "flower/" reachable)
                :target (str class-dir "/META-INF/native-image/flower/main/" reachable)})
  (b/copy-file {:src live-reload
                :target (str class-dir "/" live-reload)})
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
   "--no-fallback"
   (when is-linux "--gc=G1")
   (when dev "-Ob")
   "--exact-reachability-metadata"
   "--features=clj_easy.graal_build_time.InitClojureClasses"
   (str "--initialize-at-build-time=" (str/join "," java-interop))])

(defn graal [dev] (str/join " " (args dev)))

(defn -native-helper [dev]
  (println (graal dev))
  (uberjar nil)
  (ps/shell (graal dev)))

(defn native [_] (-native-helper false))
(defn native-dev [_] (-native-helper true))
