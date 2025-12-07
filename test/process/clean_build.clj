(ns test.process.clean-build
  (:require
   [babashka.fs :as fs]
   [clj-commons.digest :as digest]
   [clojure.string :as str]
   [clojure.test :refer [use-fixtures]]
   [expectations.clojure.test :refer [defexpect expect]]
   [test.helpers :refer [build-assert-no-rebuild exit-success flower!
                         flower-cli once-fixture]]))

(use-fixtures :once once-fixture)

(defn tmpdir [] (str (fs/create-temp-dir {:prefix "flower-clean-build-"})))
(defn / [dir & args] (str (apply fs/path dir args)))

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

(defn has-contents [page str]
  (str/includes? (String. (fs/read-all-bytes page)) str))

; unfortunately this doesn't thing we actually care about, which is that `flower watch` notices it needs to rebuild :/
(defexpect depfile-modified []
  (let [site (build-new)
        other-dir (tmpdir)
        dynamic-dep (/ other-dir "README.md")
        in (/ site "pages" "hello.md")
        out (/ site "public" "hello.html")
        x (apply str (repeat 25 "x"))
        y (apply str (repeat 25 "y"))]
    (println site dynamic-dep)
    ; Add a file that has an implicit dependency somewhere we don't know about.
    (spit dynamic-dep x)
    (spit in (str "◊" '(require '[expressions.meta :refer [include]])
                  "◊" `(~'include ~dynamic-dep)))
    (expect exit-success (build-assert-no-rebuild site))
    (expect (has-contents out x))
    ; Modify the dependency and make sure the site gets rebuilt.
    (spit dynamic-dep y)
    (expect exit-success (build-assert-no-rebuild site))
    (expect (has-contents out y))))
