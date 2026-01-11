(ns test.helpers 
  (:require
   [babashka.fs :as fs]
   [babashka.process :as ps]
   [expectations.clojure.test :refer [expect]]
   [flower.eval :as eval]
   [flower.utils :as utils :refer [*site*]])
  (:import
   [java.io StringWriter]))

(def flower-cli (fs/absolutize "target/flower"))

(defn render [s filename]
  (eval/preprocess-sunflower
    {:content s
     :filename filename}))

(defn system!
  [desc opts & args]
  (try (apply ps/shell opts args)
       (catch clojure.lang.ExceptionInfo e
         (throw (ex-info desc (ex-data e))))))

(defn flower! [dir opts & args]
  (binding [*site* dir]
    (apply flower.utils/system! opts args)))

(defn build-assert-no-rebuild
  ([dir] (build-assert-no-rebuild dir {} {}))
  ([dir settings] (build-assert-no-rebuild dir settings {}))
  ([dir settings opts]
   (let [set (flatten
               (for [[k v] settings]
                 ["--set" (format "%s=%s" (name k) v)]))
         out (apply flower! dir opts flower-cli "build" set)
         ninja (apply flower! dir {:out :string} (utils/ninja "-n -d explain"))]
     (expect 0 (:exit ninja))
     (expect "ninja: no work to do.\n" (:out ninja))
     out)))

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

;; Build once before all specs
(defn once-fixture [f]
  (build-flower!)
  (f))
