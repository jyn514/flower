(ns test.hybrid.render-cmd
  (:require
   [babashka.fs :as fs]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [expectations.clojure.test :refer [defexpect expect]]
   [flower.cli :refer [render-cmd]]
   [flower.frontmatter :refer [parse-frontmatter print-frontmatter]]))

(defn tmpdir []
  (fs/create-temp-dir {:prefix "flower-hybrid-test-render-"}))

(defn render-standalone [content & {:keys [filename]}]
  (let [dir (tmpdir)
        filename (or filename (str (gensym "unchanged") ".clj"))
        path (str (fs/path dir filename))]
    (spit path content)
    (with-out-str
      (render-cmd {:filename path :content content}))))

(defn expect-unchanged [s]
  (expect s (render-standalone s)))

(defn expect-empty [s]
  (expect "" (render-standalone s) s))

(defexpect simple
  (expect-unchanged "\n")
  (expect-unchanged "abc\n")
  (expect-unchanged "===\n{\n  \"x\": true\n}\n===\n")
  (expect-empty "===\n===\n")
  (expect-empty "===\n===")
  (expect "a" (render-standalone "◊(def x \"a\")◊x")))

;; proptests

(def json-serializable-key
  (gen/one-of [gen/keyword gen/keyword-ns]))

; see impl of gen/simple-type-printable
; doesn't print char, ratio, keyword, uuid, NaN, or inf
(def json-serializable-value
  (gen/one-of [gen/small-integer gen/size-bounded-bigint gen/string-ascii
               gen/boolean (gen/double* {:infinite? false :NaN? false})]))

; see impl of container-type
; doesn't use sets or lists, always uses keywords as key
(defn json-serializable-container [inner-gen]
  (gen/one-of [(gen/vector inner-gen)
               ;; scaling this by half since it naturally generates twice
               ;; as many elements
               (gen/scale #(quot % 2)
                          (gen/map json-serializable-key inner-gen))]))

; see impl of gen/any-printable
(def json-recursive-container
  (gen/recursive-gen json-serializable-container json-serializable-value))

(def gen-frontmatter
  (gen/not-empty (gen/map json-serializable-key json-recursive-container)))

(defspec can-roundtrip-frontmatter 100
  (prop/for-all [hashmap gen-frontmatter]
    (let [serialized (with-out-str (print-frontmatter hashmap))
          [deserialized _] (parse-frontmatter "<proptest>" (render-standalone serialized))]
      (expect some? serialized hashmap)
      (expect hashmap deserialized))))
