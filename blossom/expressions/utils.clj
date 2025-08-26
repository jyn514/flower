(ns expressions.utils)

(defn split-all [f seq]
  [(filter f seq) (filter #(not (f %)) seq)])

; https://stackoverflow.com/a/41049094
(defmacro as-map
  "Given (as-map a b c), returns {:a a :b b :c c}"
  [& syms]
  (zipmap (map keyword syms) syms))

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

(defn insert-after-where
  "Inserts `item` into `coll` after the first element that satisfies `pred`."
  [coll pred item]
  (let [[after before] (split-with pred coll)]
    (concat before (take 1 after) (list item) (drop 1 after))))
