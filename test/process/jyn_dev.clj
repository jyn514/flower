(ns process.jyn-dev
  (:require
   [clojure.test :refer [use-fixtures]]
   [expectations.clojure.test :refer [defexpect expect]]
   [process.e2e :refer [flower-cli build-flower! flower! require-exe!]]))

(defn- once-fixture [f]
  (require-exe! "clojure")
  (require-exe! "ninja")
  (build-flower!)
  (f))

(use-fixtures :once once-fixture)

(defexpect build-jyn-dev []
  (let [{:keys [exit]} (flower! "blossom" flower-cli "build")]
    (expect 0 exit)))
