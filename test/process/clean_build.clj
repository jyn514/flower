(ns test.process.clean-build
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [use-fixtures]]
   [expectations.clojure.test :refer [defexpect expect]]
   [test.helpers :refer [build-assert-no-rebuild exit-success flower!
                         flower-cli once-fixture]]))

(use-fixtures :once once-fixture)

(defn tmpdir [] (str (fs/create-temp-dir {:prefix "flower-clean-build-"})))

(defexpect clean-build []
  (let [dir (tmpdir)]
    (expect exit-success (flower! dir flower-cli "new"))
    (expect exit-success (build-assert-no-rebuild dir))))

(defexpect clean-dead []
  (let [dir (tmpdir)
        in (fs/path dir "pages" "index.html")
        out (fs/path dir "public" "index.html")]
    (println dir)
    (expect exit-success (flower! dir flower-cli "new"))
    (expect exit-success (build-assert-no-rebuild dir))
    (expect (fs/exists? out))
    (fs/delete in)
    (expect exit-success (build-assert-no-rebuild dir))
    (expect (not (fs/exists? out)))))
