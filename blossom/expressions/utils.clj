(ns expressions.utils)

(defn split-all [f seq]
  [(filter f seq) (filter #(not (f %)) seq)])

; https://clojuredocs.org/clojure.core/destructure#example-5a946a0ae4b0316c0f44f8f2

(defmacro def+
  "binding => binding-form
  internalizes binding-forms as if by def."
  {:forms '[(def+ [bindings*])]}
  [& bindings]
  (let [bings (partition 2 (destructure bindings))]
    (sequence cat 
      ['(do) 
       (map (fn [[var value]] `(def ~var ~value)) bings)])))
