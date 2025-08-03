; TODO: rename to flower.postprocess
(ns flower.select
  (:require [clojure.zip :as zip])
  (:import (org.jsoup.nodes LeafNode Node Document Element Attribute Attributes)
           (org.jsoup.select Elements)
           (org.jsoup Jsoup)))

(declare after!)

; TODO: a bunch of these functions make sense on Elements, not just Element
; — maybe allow that?

(defn select
  "Given an HTML document and a CSS selector, return a `org.jsoup.nodes.Elements` of matching elements"
  [doc ^String selector]
  (let [doc (if (string? doc) (Jsoup/parse ^String doc) doc)]
    (Element/.select doc selector)))

(defn html
  "Given an HTML Element, return its innerHtml() as a string."
  [node]
  (Element/.html node))

(defn replace-with!
  "Given an HTML Element and an unparsed HTML document,
   replace the element with HTML."
  [node html]
  (after! node html)
  (Element/.remove node))


; after before append prepend attrs set-attr remove-attr remove replace-with
; these have to be explicitly bound because SCI sandboxes java by default
; (defn append [& rest] (apply #(.append %&) rest))

; TODO: this only works on a list of elements lol, make it work on an individual Element too
(defn append! [n c] (Elements/.append n (str c)))
(defn  after! [n c] (Element/.after n (str c)))

(defn attrs
  "Given an HTML Element, return its attributes as a clojure map from string to string.
  Note that attribute names (keys) are normalized to lower-case."
  [n]
  (let [java-attrs (Element/.attributes n)
        iter (iterator-seq (Attributes/.iterator java-attrs))
        key-vals (map #(do [(Attribute/.getKey %) (Attribute/.getValue %)]) iter)]
    (into {} key-vals)))

; (def d (Jsoup/parse "<div><h1>hiiiii</h1></div>"))
; (def z (zipper d))

; (defn zipper
;   "Given an `org.jsoup.nodes.Element`, return a `clojure.zip` zipper structure."
;   ([doc selector] (zipper (select doc selector)))
;   ([elem]
;   (zip/zipper
;     #(not (instance? LeafNode %))
;     #(Node/.childNodes %)
;     #(let [n (Node/.shallowClone %)]
;        (Node/.addChildren n %2)
;        (Node/.setParentNode n (Node/.parent %))
;        n)
;     ; TODO: starts from halfway through a DOM.
;     ; start from the root scrolled to `elem` instead, using https://github.com/igrishaev/zippo#lookups
;     elem)))
