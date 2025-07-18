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
    (sci/eval-string (str "(print-str " lisp ")")))
} tree))

; TODO: frontmatter parser
; see https://github.com/liquidz/frontmatter/blob/master/src/frontmatter/core.clj
; for an example, it's almost trivial. adding it to instaparse is hard though because we'd need to slot in an external parser.
; https://github.com/Engelberg/instaparse#combinators *maybe* would work
(defn render [src] (teval (parse src) src))

(defn renderf [in out] ; [] (let [[in out] (*command-line-args*)]
  (spit out (render (slurp in))))
; (print "hi")
(def -main renderf)
(apply renderf *command-line-args*)
; (let [[in out] *command-line-args*] (renderf in out))

; ; https://babashka.org/
; ; https://github.com/weavejester/hiccup
; ; for repl
(def src "x◊(+ 1 2)")

(ns blossom-test (:require [clojure.test :as t])
  (:require [blossom]))
; (defmacro desc t/testing)
(t/deftest parser
  (t/testing "accepts valid"
    (t/is (= "x3" (blossom/render blossom/src))))
  (t/testing "any start"
    (t/is (= "3x" (blossom/render "◊(+ 1 2)x"))))
  (t/testing "errors"
    (t/is (= instaparse.gll.Failure (type (blossom/render "◊("))))))
(t/deftest eval-values
  (t/testing "lazy collections ok"
    (t/is (= "(2 3 4)" (blossom/render "◊(map inc [1 2 3])")))))

; (t/run-tests)
