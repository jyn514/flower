(ns flower.select
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
  ([doc selector] (zipper (select doc selector)))
  ([elem]
  (zip/zipper
    #(not (instance? LeafNode %))
    #(.childNodes %)
    #(let [n (.shallowClone %)]
       (.addChildren n %2)
       (.setParentNode (.parent %))
       n)
    ; TODO: starts from halfway through a DOM.
    ; start from the root scrolled to `elem` instead, using https://github.com/igrishaev/zippo#lookups
    elem)))

; after before append prepend attrs set-attr remove-attr remove replace-with
; these have to be explicitly bound because SCI sandboxes java by default
; (defn append [& rest] (apply #(.append %&) rest))
(defn append [n c] (.append n (str c)))

(def d (Jsoup/parse "<div><h1>hiiiii</h1></div>"))
(def z (zipper d))
