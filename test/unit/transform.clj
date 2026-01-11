
(ns test.unit.transform 
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is]]
   [expectations.clojure.test :refer [expect]]
   [expressions.transform-sorter :as transform-sorter]
   [flower.cmd :as cmd]
   [flower.frontmatter :refer [write-json]]
   [flower.stacktrace :refer [print-trace]]
   [flower.unsafe :as unsafe]
   [flower.utils :as utils :refer [merge-deep remove-parent]]))

(defn default-transformer [name]
  (fs/path "defaults" "transformers" (str name ".clj")))

(def all-transformers
  (->> (fs/glob "defaults/transformers" "*") (sort transform-sorter/trans-sorter) (map remove-parent)))

(defn run-all
  ([page] (run-all page {}))
  ([page opts]
   (let [opts (merge {:transformers all-transformers :standalone true} opts)
         page (merge-deep {:frontmatter {:flower/source-file "<transform test>"}} page)
         serialized (with-out-str (write-json page))]
     (binding [unsafe/*dependencies* #{}
               unsafe/*drop-bomb* false
               utils/*site* "defaults"]
       (try
         (with-in-str serialized
           (with-out-str
             (cmd/transform opts)))
         (catch java.lang.Exception e
           (is false)
           (when-not (::silent (ex-data e))
             (binding [*out* *err*]
               (print-trace e false)))))))))

(defn run-preprocessor [page]
  (run-all page
           {:transformers ["transformers/preprocess.clj" "transformers/content.clj"]
            :raw-output true}))

(deftest identity-preprocessor
  (expect "◊◊◊" (run-preprocessor {:content "◊◊◊"
                                   :frontmatter {:preprocessors []}})))
