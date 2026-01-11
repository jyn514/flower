(ns test.unit.frontmatter
  (:require
   [clojure.string :as str]
   [expectations.clojure.test :as expect :refer [defexpect expect]]
   [flower.frontmatter :refer [write-json]]))

; see (source with-out-str)
(defmacro with-err-str
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*err* s#]
       ~@body
       (str s#))))

(defn warns [msg]
  (fn [s] (str/includes? s (str "type information for " msg " will be discarded"))))

(defn expect-warns [msg val]
  (expect (warns msg) (with-err-str (write-json val)) val))

(defexpect warns-on-invalid
  (expect "" (with-err-str (write-json {:a "b"})))
  (expect "" (with-err-str (write-json {'a "b"})))
  (expect-warns "symbol b" {:a 'b})
  (expect-warns "list ()" {:a ()})
  (expect-warns "set #{}" {:a #{}})
  (expect-warns "UUID b6883c0a-0342-4007-9966-bc2dfa6b109e"
                {:a #uuid "b6883c0a-0342-4007-9966-bc2dfa6b109e"})
  (expect-warns "ratio 22/7" {:a 22/7})
  (expect-warns "keyword :b" {:a :b}))
