(ns expressions.html 
  (:use flower.utils))
(import
  (org.jsoup Jsoup)
  (org.jsoup.nodes Attribute Attributes Element XmlDeclaration)
  (org.jsoup.parser Parser)
  (org.jsoup.select Elements))

(declare after!)

; TODO: a bunch of these functions make sense on Elements, not just Element
; — maybe allow that?

(defn- is-root [doc]
  (let [fragment (if-not (string? doc) doc
                   (Jsoup/parse doc "" (Parser/xmlParser)))
        root (-> fragment .ownerDocument .firstChild)]
    (or (instance? XmlDeclaration root) 
        (boolean (some #{root} ["html" "#doctype"])))))

(defn ->element
  "Convert an HTML string into a parsed HTML Element"
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
    (let [html (Jsoup/parse doc)]
      (if (is-root doc) html (.body html)))))

(defn document
  "Given an HTML element, get the root document element.
   The root may not necessarily be <html> if this was parsed from an element fragment."
   [doc]
   (Element/.ownerDocument (->element doc)))

(defn select
  "Given an HTML document and a CSS selector, return a `org.jsoup.nodes.Elements` of matching elements"
  [doc ^String selector]
  (Element/.select (->element doc) selector))

(defn text
  "Given an HTML Element, return the normalized, combined text
   of this element and all its children."
  [node]
  (Element/.text node))

(defn innerHtml
  "Given an HTML Element, return its innerHtml() as a string."
  [node]
  (Element/.html node))

(defn parent
  "Given an HTML Element, return its parent() as a string."
  [node]
  (Element/.parent node))

(defn replace-with!
  "Given an HTML Element and an unparsed HTML document,
   replace the element with HTML."
  [node html]
  (let [parsed (->element html)
        node (if (is-root parsed) (document node) node)]
    (.replaceWith node parsed)
    (when (is-root parsed)
      ; replaceWith normalizes away <!doctype> >:(
      (.prependChild (.ownerDocument parsed)
                     (org.jsoup.nodes.DocumentType. "html" "" "")))))


; after before append prepend attrs set-attr remove-attr remove replace-with

; TODO: this only works on a list of elements lol, make it work on an individual Element too
(defn append! [node html] (Elements/.append node (str html)))
(defn  after! [node html] (Element/.after node (str html)))

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


