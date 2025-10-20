(ns test.process.jyn-dev
  (:require
   [clojure.test :refer [use-fixtures]]
   [expectations.clojure.test :refer [defexpect expect]]
   [test.helpers :refer [once-fixture flower-cli flower! build-assert-no-rebuild]]))

(use-fixtures :once once-fixture)

(defexpect build-jyn-dev []
  (let [{:keys [exit]} (build-assert-no-rebuild "blossom" {:rebuild-flower false})]
    (expect 0 exit)))

(defexpect build-jyn-dev []
  (let [{:keys [exit]} (build-assert-no-rebuild "docs")]
    (expect 0 exit)))
