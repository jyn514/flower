(ns expressions.constants)

; TODO: something nice based off $USER
(def site-title "it's your flower site!")

; TODO: configuration mechanism using ninja phony targets
; actually that doesn't fix this case here.
; https://codeberg.org/jyn514/flower/issues/19#issuecomment-6605005
(def use-jar true)
(def rebuild-flower false)
