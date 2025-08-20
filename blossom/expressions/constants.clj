(ns expressions.constants)

(def site-title "the website of jyn")
(def email "blog@jyn.dev")
(def github "jyn514")
(def linkedin "jynelson514")
(def global-desc "i write about code, and things that bring me joy, and sometimes other things too")

; TODO: configuration mechanism using ninja phony targets
; actually that doesn't fix this case here.
; https://codeberg.org/jyn514/flower/issues/19#issuecomment-6605005
(def use-jar false)
(def rebuild-flower true)
