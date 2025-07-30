(ns native
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str]
            [babashka.process :as ps]))

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file "target/flower.jar")
(defn clean [_]
  (b/delete {:path class-dir})
  (b/delete {:path "target/flower"})
  (b/delete {:path jar-file}))
(defn uberjar [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :ns-compile '[flower.core]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file jar-file
           :basis basis
           :main 'flower.core}))

; https://github.com/babashka/babashka/blob/e2316f1bbef9daa9e5ec801a9bcbc0ece703d076/resources/META-INF/native-image/babashka/babashka/native-image.properties#L15

(def java-interop
  ["net.thisptr.jackson.jq"
   "org.yaml.snakeyaml"
   "org.commonmark"
   "org.jsoup"
   "org.nibor.autolink"
   "com.fasterxml.jackson"])

(defn args [dev]
  ["scripts/install-graal.sh -jar" jar-file "target/flower"
   "--no-fallback --gc=G1"
   (if dev "-Ob")
   "--link-at-build-time=org.yaml.snakeyaml.Yaml"
   "--features=clj_easy.graal_build_time.InitClojureClasses"
   (str "--initialize-at-build-time=" (str/join "," java-interop))])

(defn graal [dev] (str/join " " (args dev)))

(defn native [_]
  (println (graal false))
  ; (uberjar nil)
  (ps/shell (graal false)))

(defn native-dev [_]
  (println (graal true))
  ; (uberjar nil)
  (ps/shell (graal true)))
