(ns expressions.shortcodes
  (:use flower.utils)
  (:require [flower.reflect :as reflect]))
(defn note
  ([content] (note false content))
  ([hide content] (inspect (reflect/render-file
    ; NOTE: this is often evaluated in a markdown context;
    ; add whitespace so the markdown renderer doesn't escape it
    "\n\n<div class=note-container>
      ◊(if hide)
        «<details class=note-content><summary>◊hide</summary>»
        «<div class=note-content>»
      \n\n ◊content \n\n
      </◊(if hide)«details»«div»></div>\n\n"
    "<note-shortcode>"
    {'content content 'hide hide}))))


