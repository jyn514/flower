(ns test.helpers 
  (:require
   [babashka.fs :as fs]
   [babashka.process :as ps]
   [flower.utils :refer [*site*]])
  (:import
   [java.io StringWriter]))

(def flower-cli (fs/absolutize "target/flower"))

(defn system!
  [desc opts & args]
  (try (apply ps/shell opts args)
       (catch clojure.lang.ExceptionInfo e
         (throw (ex-info desc (ex-data e))))))

(defn flower! [dir opts & args]
  (binding [*site* dir]
    (apply flower.utils/system! opts args)))

(defn require-exe! [cmd]
  (system! (str "Required executable not found: '" cmd "'")
           {:out (StringWriter.)} cmd "--version"))

(defn build-flower! []
  (when-not (System/getenv "CI")
    (require-exe! "clojure")
    (system! "Failed to build flower executable" "ninja flower-bin"))
  (when-not (fs/exists? flower-cli)
    (throw (ex-info "Missing CLI script target/flower after build" {}))))

(defn exit-success [proc]
  (-> proc :exit (= 0)))

;; Build once before all specs; ensure required tools exist
(defn once-fixture [f]
  (require-exe! "ninja")
  (build-flower!)
  (f))

