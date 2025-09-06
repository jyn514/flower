(ns expressions.shortcodes
  (:require
   [expressions.meta :refer [embed]]
   [hiccup2.core :as h]))

(defn img
  ([src] (img {} src))
  ([opts src] (h/html [:img (assoc opts :src src)])))

(def expand-button
  "\n\n<button id=expandAll>Click here to open all notes.</button>\n\n")

(defn note
  ([body] (note false body))
  ([hide body]
   (embed "note.html" {'hide hide 'body body})))
