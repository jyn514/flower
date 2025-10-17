(ns test.process.clean-build
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [use-fixtures]]
   [expectations.clojure.test :refer [defexpect expect]]
   [test.helpers :refer [exit-success flower! flower-cli once-fixture]]))

(use-fixtures :once once-fixture)

(defexpect clean-build []
  (let [dir (str (fs/create-temp-dir {:prefix "flower-clean-build-"}))]
    (expect exit-success (flower! dir flower-cli "new"))
    (expect exit-success (flower! dir flower-cli "build"))))
