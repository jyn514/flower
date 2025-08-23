(ns parser
  (:require
   [clojure.string :as str]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [expectations.clojure.test :as expect :refer [expect]]
   [flower.eval :as eval]
   [instaparse.core :as insta]))

(def gen-sunflower-ident (gen/fmap pr-str gen/symbol))
(def gen-lisp (gen/fmap pr-str gen/any))
(def gen-inline-render
  (gen/fmap (fn [[call body]] (str (pr-str call) "{" body "}"))
            (gen/tuple gen-lisp gen/string-ascii)))
(def gen-sunflower-cmd
  (let [syntax (gen/one-of [gen-sunflower-ident gen-lisp gen-inline-render])]
    (gen/fmap #(str "◊" %) syntax)))

(def gen-sunflower
  (gen/fmap str/join
            (gen/vector (gen/one-of [gen/string gen-sunflower-cmd]))))

(defn unambiguous? [parses]
  (< (count parses) 2))

(defspec unambiguous 100
  (prop/for-all [s gen-sunflower]
    (let [parsed (insta/parses eval/parse s)]
      (expect unambiguous? parsed))))
