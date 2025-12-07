(ns test.process.jyn-dev
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [use-fixtures]]
   [expectations.clojure.test :refer [defexpect expect]]
   [test.helpers :refer [build-assert-no-rebuild once-fixture]]))

(use-fixtures :once once-fixture)

(defexpect build-jyn-dev []
  (fs/delete-tree "blossom/.build")
  (let [{:keys [exit]} (build-assert-no-rebuild "blossom" {:rebuild-flower false})]
    (expect 0 exit)))

(defexpect build-docs []
  (fs/delete-tree "docs/.build")
  (let [{:keys [exit]} (build-assert-no-rebuild "docs")]
    (expect 0 exit)))
