(ns test.process.clean-build
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [use-fixtures]]
   [expectations.clojure.test :refer [defexpect expect]]
   [test.helpers :refer [build-assert-no-rebuild exit-success flower!
                         flower-cli once-fixture]]))

(use-fixtures :once once-fixture)

(defn tmpdir [] (str (fs/create-temp-dir {:prefix "flower-clean-build-"})))

(defn build-new []
  (let [dir (tmpdir)]
    (expect exit-success (flower! dir flower-cli "new"))
    (expect exit-success (build-assert-no-rebuild dir))
    dir))

(defexpect clean-build [] (do (build-new) []))

(defexpect clean-dead []
  (let [dir (build-new)
        in (fs/path dir "pages" "index.html")
        out (fs/path dir "public" "index.html")]
    (expect (fs/exists? out))
    (fs/delete in)
    (expect exit-success (build-assert-no-rebuild dir))
    (expect (not (fs/exists? out)))))

(defexpect new-post []
  (let [dir (build-new)]
    ; Make sure that ninja notices and rebuilds when a new file is added.
    (spit (str (fs/path dir "pages" "my-new-post.md")) "this is some *markdown*!")
    (expect exit-success (build-assert-no-rebuild dir))
    (expect (fs/exists? (fs/path dir "public" "my-new-post.html")))))
