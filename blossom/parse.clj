(ns blossom
  (:require [instaparse.core :as insta])
  (:require [sci.core :as sci]))
; REPL
; (add-lib 'instaparse/instaparse)
; (require '(instaparse [core :as insta]))
(def parse
   (insta/parser
     "Start = (Text | Lisp)*
      Text = #'[^◊]+'
      Lisp = <'◊'> List
      List = <'('> (Atom | List)* <')'>
      Atom = #'[^()]*' "))

(defn teval [tree src] (insta/transform {
  :Start str,
  :Text identity,
  :Lisp (fn [l]
    (def lisp (apply subs src (insta/span l)))
    ; TODO: add standard library
    (sci/eval-string lisp))
} tree))

(defn render [src] (teval (parse src) src))
; ; https://github.com/babashka/sci
; ; https://babashka.org/
; ; https://github.com/weavejester/hiccup
; ; for repl
(def src "x◊(+ 1 2)")
