(ns blossom.select
  (:require [clojure.zip :as zip])
  (:import (org.jsoup.nodes LeafNode Node Document Element)
           (org.jsoup Jsoup)))

(defn select
  "Given an HTML document and a CSS selector, return a `org.jsoup.nodes.Elements` of matching elements"
  [doc selector]
  (let [doc (if (string? doc) (Jsoup/parse doc) doc)]
    (.select doc selector)))

(defn zipper
  "Given an `org.jsoup.nodes.Element`, return a `clojure.zip` zipper structure."
  [elem]
  (zip/zipper
    #(not (instance? LeafNode %))
    #(.children %)
    ; #(seq (.children %))
    #(let [n (.shallowClone %)]
       (.addChildren n %2)
       n)
    elem))
; after before append prepend attrs set-attr remove-attr remove replace-with

(def d (Jsoup/parse "<div><h1>hiiiii</h1></div>"))
(def z (zipper d))
