(ns test.hybrid.defaults
  (:require
   [babashka.fs :as fs :refer [delete-tree]]
   [expectations.clojure.test :refer [defexpect expect]]
   [flower.defaults :refer [defaults-path materialize-all]]
   [flower.utils :refer [*site* git-hash try-slurp]]))

(defn / [& args] (str (apply fs/path *site* args)))

(defn version [] (/ ".build" "version"))

(defn tmpdir []
  (fs/create-temp-dir {:prefix "flower-hybrid-test-defaults-"}))

(defn materialized? [path]
  (and (->> path / fs/exists?)
       (->> path (/ ".build" "defaults") fs/exists? not)))

(defn virtual? [path]
  (and (->> path / fs/exists? not)
       (->> path (/ ".build" "defaults") fs/exists?)))

(defmacro defsite
  [name & body]
  `(defexpect ~name
    (binding [*site* (tmpdir)]
      (println (str *site*))
      (materialize-all {})
      ~@body)))

(defsite always-materialized
    (expect (fs/exists? (version)))
    (expect (try-slurp (version)) (git-hash))
    (expect (not (fs/exists? (/ ".build" "MANIFEST.txt"))))
    (expect (materialized? "flower.edn"))
    (expect (materialized? "templates/default.html"))
    (expect (virtual? "build.clj"))
    (expect (virtual? "expressions/ninja.clj"))
    (expect (virtual? "transformers/markdown.clj"))
    (expect (virtual? "transformers/standalone/generate_redirect.clj")))

(defsite modified
  ; delete it and make sure our test fails
  (fs/delete (defaults-path "build.clj"))
  (expect (not (virtual? "build.clj")))
  ; run flower new. nothing should happen, since the version hasn't changed.
  (materialize-all)
  (expect (not (virtual? "build.clj")))
  ; change the version and run flower new again. we should see build.clj reinitialize.
  (spit (version) "xxxxxx")
  (materialize-all)
  (expect (virtual? "build.clj"))
  ; now delete the build dir altogether and make sure it's regenerated
  (delete-tree (/ ".build"))
  (materialize-all)
  (expect (virtual? "build.clj")))
