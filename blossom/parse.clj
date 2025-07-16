(ns blossom
  (:require [instaparse.core :as insta]))
; REPL
; (add-lib 'instaparse/instaparse)
; (require '(instaparse [core :as insta]))
(def parse
   (insta/parser
     "Start = (Text | Lisp)*
      Text = #'[^◊]+'
      Lisp = '◊' List
      List = '(' (Atom | List)* ')'
      Atom = #'[^()]*' "))

(defn treduce [l]
  (if (string? l) l
    (reduce (fn [x y] (if string? y)
              (str x y)
              (str x "(" (map treduce y) ")")) "" l)))

(defmulti tquote first)
(defmethod tquote :List [[_ _ & xs]]
  (let [args (drop-last xs)]
    (map tquote args)))
(defmethod tquote :Atom [[_ t]] t)

  ; https://github.com/babashka/sci

(defmulti teval (fn [x] (if (sequential? x) (first x) x)))
(defmethod teval :Start [_] "")
(defmethod teval :Text [[_ t]] t)
(defmethod teval :Lisp [[_ _ l]] (tquote l))

; for repl
(def tree (parse "x◊(a (b c))"))
(def p (first (drop 2 (map teval tree))))

(def lisp (get tree 2))
(def l (last lisp))
