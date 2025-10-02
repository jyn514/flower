(ns expressions.html
  (:require
   [clojure.string :as str]
   [expressions.utils :refer [inspect]])
  (:import
   (org.jsoup Jsoup)
   (org.jsoup.nodes
    Attribute
    Attributes
    Element
    XmlDeclaration)
   (org.jsoup.parser Parser)
   (org.jsoup.select Elements)))

; TODO: a bunch of these functions make sense on Elements, not just Element
; — maybe allow that?

(defn- tag-root? [element]
  (let [tag (.nodeName element)]
    (or (instance? XmlDeclaration tag) 
        (some #{tag} ["html" "#doctype"]))))

(defn- document-root? [doc]
  (if-not (string? doc) (tag-root? doc)
    (let [fragment (Jsoup/parse (str/triml doc) "" (Parser/xmlParser))
          root (some-> fragment .ownerDocument .firstChild)]
      (tag-root? root))))

(defn ->element
  "Convert an HTML string into a parsed HTML Element (or list of Elements)"
  [doc]
  (if-not (string? doc) doc
    ; we want to preserve the html exactly as written.
    ; unfortunately, Jsoup tries to do lots of normalization.
    ; things that don't work:
    ; - Jsoup/parse (adds surrounding html/body)
    ; - Jsoup/parseFragment (as far as i can tell, the same as /parse)
    ; - wrapping in <template> (strips any <html> tags, so it doesn't work for skeleton.html)
    ; - Parser.xmlParser (tries to add closing tags for self-closing tags)
    ; instead, do a really dumb thing:
    ; first, check if this has an existing <html> tag or not by parsing it with XML.
    ; then, decide whether to call .body based on that.
    ;
    ; NOTE: <!DOCTYPE html> must be the very first thing in a document,
    ; including whitespace. Strip whitespace so we aren't thrown off by `◊` escapes.
    ; TODO: https://codeberg.org/jyn514/flower/issues/23
    (let [stripped (str/trim doc)
          html (Jsoup/parse stripped)]
      (if (document-root? stripped) html
        (let [children (-> html .body .children)]
          (if (= 1 (count children)) (first children)
            children))))))

(defn document
  "Given an HTML element, get the root document element.
   The root may not necessarily be <html> if this was parsed from an element fragment."
   [doc]
   (Element/.ownerDocument (->element doc)))

(defn select
  "Given an HTML document and a CSS selector, return a `org.jsoup.nodes.Elements` of matching elements"
  [doc ^String selector]
  (.select (->element doc) selector))

(defn tag
  "Given an HTML element, return the tag name."
  [e]
  (.nodeName e))

(defn text-content
  "Given an HTML Element, return the normalized, combined text
   of this element and all its children."
  [node]
  (Element/.text node))

(defn inner-html
  "Given an HTML Element, return the combined HTML of all children as a string.
   Note that this will escape any text using HTML entities.
   If you don't want that behavior, consider using `text-content`."
  [node]
  (Element/.html node))

(defn parent
  "Given an HTML Element, return its parent() as a string."
  [node]
  (Element/.parent node))

(defn replace-children!
  "Given an HTML Element and an unparsed HTML document,
   replace the element's children with the new document."
   [node re]
   (Element/.html node re))

(defn replace-with!
  "Given an HTML Element and an unparsed HTML document,
   replace the element with the new document."
  [node re]
  (let [parsed (->element re)
        node (if (document-root? parsed) (document node) node)]
    (.replaceWith node parsed)
    (when (document-root? parsed)
      ; replaceWith normalizes away <!doctype> >:(
      (.prependChild (.ownerDocument parsed)
                     (org.jsoup.nodes.DocumentType. "html" "" "")))))


; TODO: before prepend remove

; TODO: this only works on a list of elements lol, make it work on an individual Element too
(defn append! [node html] (Elements/.append node (str html)))
(defn  after! [node html] (Element/.after   node (str html)))

(defn attrs
  "Given an HTML Element, return its attributes as a clojure map from keyword to string.
  Note that attribute names (keys) are normalized to lower-case."
  [n]
  (let [java-attrs (Element/.attributes n)
        iter (iterator-seq (Attributes/.iterator java-attrs))
        key-vals (map #(do [(keyword (Attribute/.getKey %)) (Attribute/.getValue %)]) iter)]
    (into {} key-vals)))

(defn set-attr!
  "Add an attribute to an HTML element.
  'key' can be either a keyword, symbol, or a string.
  'val' can be either a string or a boolean."
  [node key val]
  (Element/.attr node (name key) val))

(defn remove-attr!
  "Remove an attribute from an HTML element.
  'key' can be either a keyword, symbol, or a string."
  [node key]
  (Element/.removeAttr node (name key)))

(defn add-class!
  [node name]
  (Element/.addClass node name))

(defn remove-class!
  [node name]
  (Element/.removeClass node name))

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


