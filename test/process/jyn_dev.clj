(ns test.process.jyn-dev
  (:require
   [clojure.test :refer [use-fixtures]]
   [expectations.clojure.test :refer [defexpect expect]]
   [test.helpers :refer [once-fixture flower-cli flower!]]))

(use-fixtures :once once-fixture)

(defexpect build-jyn-dev []
  (let [{:keys [exit]} (flower! "blossom" flower-cli "build" "--set" "rebuild-flower=false")]
    (expect 0 exit)))
