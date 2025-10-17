#!/usr/bin/env clojure

(require '[clojure.java.shell :refer [sh]])
(require '[clojure.java.io :as io])
(require '[clojure.string :as str])

(def graal-version 24) ; update in `.envrc` too
(def os
  (condp #(str/starts-with? %2 %1) (System/getProperty "os.name")
    "Windows" "windows"
    "Mac" "macos"
    "Linux" "linux"))
(def arch
  (case (System/getProperty "os.arch")
    "amd64" "x64"
    "aarch64" "aarch64"))
(def ext (case os
           "windows" "zip"
           "tar.gz"))
(def url (str "https://download.oracle.com/graalvm/" graal-version "/latest/graalvm-jdk-"
              graal-version "_" os "-" arch "_bin." ext))
(def archive-file (str "graalvm-jdk." ext))
(def out-dir (str "target/graalvm-jdk-" graal-version))

;; Download archive
(println "Downloading graal ...")
(io/make-parents (str out-dir "/some-file"))
(with-open [in (io/input-stream url)
            archive-file (io/output-stream archive-file)]
  (io/copy in archive-file))

;; Extract archive
(println "Extracting graal ...")
(sh "tar" "--strip-components=1" "-C" out-dir "-xf" archive-file) ; TODO(windows): `zip` instead of `tar`

;; Delete archive
(println "Deleting archive ...")
(io/delete-file archive-file)

;; Check version
(println "Testing graal ...")
(case os
  "macos" (sh (str out-dir "/Contents/Home/bin/native-image") "--version")
  (sh (str out-dir "/bin/native-image") "--version"))

(println "Done!")

(shutdown-agents)
