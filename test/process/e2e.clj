(ns test.process.e2e
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [clojure.test :refer [use-fixtures]]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [expectations.clojure.test :refer [expect]]
   [flower.utils :refer [remove-parent]]
   [test.helpers :refer [flower! flower-cli once-fixture]])
  (:import
   [java.io StringWriter]))

;; --- helpers ---------------------------------------------------------------

(defn unique-by [k coll]
  (vals (into {} (map (juxt k identity) coll))))

(defn- slug
  [s]
  (-> s str/lower-case (str/replace #"[^a-z0-9-]" "-")))

(defn- write-file! [root rel content]
  (let [dst (fs/path root rel)]
    (fs/create-dirs (fs/parent dst))
    (spit (str dst) content)))

(use-fixtures :once once-fixture)

;; --- generators ------------------------------------------------------------

(def gen-safe-text
  "Generate text without Flower escapes (◊ or ⋄)."
  ; >:( test.check doesn't seem to have a way to generate printable unicode
  (gen/such-that #(not-any? #{\◊\⋄} %) gen/string-ascii))

(def gen-basename
  (gen/fmap str/join
    (gen/vector gen/char-alphanumeric 1)))

(def gen-ext (gen/frequency [[4 (gen/return "md")] [1 (gen/return "html")]]))

(def gen-relpath
  (gen/let [dirs (gen/vector gen-basename 0 2)
            base gen-basename
            ext gen-ext]
    (str (str/join "/" (concat ["pages"] dirs [base])) "." ext)))

; TODO: enforce unique path
(def gen-frontmatter
  (gen/let [title gen/string-alphanumeric
            override-path? gen/boolean
            out-name gen-basename]
    (let [maybe-path (when override-path? (str out-name ".html"))]
      {:title (or title "Untitled")
       :path maybe-path})))

(def gen-page
  (gen/let [rel gen-relpath
            fm gen-frontmatter
            body (gen/fmap #(str/join "\n\n" %)
                           (gen/vector gen-safe-text 1 6))]
    {:rel rel :fm fm :body body}))

(def gen-site
  (gen/fmap
    (fn [pages]
      ;; Enforce unique basenames to avoid output collisions
      (let [unique-inputs (unique-by (fn [{:keys [rel]}]
                                (-> rel fs/file-name fs/strip-ext))
                              pages)
            unique-outputs (unique-by #(-> % :fm :page) unique-inputs)]
        (vec (take (max 1 (min 5 (count unique-outputs))) unique-outputs))))
    (gen/vector gen-page 1 5)))

;; --- end-to-end property ---------------------------------------------------

(defn- expected-output [site {:keys [rel fm]}]
  (let [rel-path (-> rel remove-parent fs/strip-ext)
        out (or (:path fm) (str rel-path ".html"))]
    (str (fs/path site "public" out)))
  )

(defn- rm-default-pages! [site]
  (doseq [p (fs/glob (fs/path site "pages") "**")
          :when (and (fs/exists? p) (not (fs/directory? p)))]
    (fs/delete-if-exists p)))

(defn- materialize-site! [site pages]
  ;; Use the shipped defaults as a base
  (flower! site flower-cli "new")
  ;; Remove example content
  (rm-default-pages! site)
  ;; Write generated pages
  (doseq [{:keys [rel fm body]} pages]
    (let [front (format "---\n%s%s---\n\n"
                        (format "title: %s\n" (:title fm))
                        (if-let [p (:path fm)] (format "path: %s\n" p) ""))

          content (str front body)]
      (write-file! site rel content))))

(defn exit-success [proc]
  (-> proc :exit (= 0)))

(defspec smoke-test-no-escapes 30
  (prop/for-all [site gen-site]
    ;; isolate each trial in its own temp dir
    (let [dir (str (fs/create-temp-dir {:prefix "flower-site-"}))
          opts {:out (StringWriter.)
                :continue true}]
      (materialize-site! dir site)
      (when (expect exit-success (flower! dir opts flower-cli "build"))
        (let [outs (map #(expected-output dir %) site)]
          (doseq [f outs]
            (when (expect fs/exists? f)
              (expect #(> (fs/size %) 0) f)))))
      ; only delete site if test succeeds
      (fs/delete-tree site)
      ; otherwise for-all fails the test
      true)))

