(ns expressions.utils 
  (:require
   [clojure.set :refer [union]]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [flower.fs :as fs]))

(defn discard
  "Run a function for its side-effects and discard the return value.
   Useful if you don't want to emit the return value into a template."
  [f & args]
  (do (apply f args) nil))

(defn inspect 
  "Debugging. Prints its argument and returns it."
  [x] (prn x) x)

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

(defmacro fmt
  "Format string mini-language.
   Allows using `${var}` in a format string to refer to a variable in scope."
  [^String string]
  (let [-re #"\$\{(.*?)\}"
        fstr (str/replace string -re "%s")
        fargs (map #(read-string (second %)) (re-seq -re string))]
    `(format ~fstr ~@fargs)))

(defn insert-after-where
  "Inserts `item` into `coll` after the first element that satisfies `pred`."
  [pred item coll]
  (let [[after before] (split-with pred coll)]
    (concat before (take 1 after) (list item) (drop 1 after))))

; https://groups.google.com/g/clojure/c/UdFLYjLvNRs/m/8fd9fvNur6cJ
(defn merge-deep [& xs]
  (cond
    (every? map? xs) (apply merge-with merge-deep xs)
    (every? set? xs) (apply union xs)
    (every? sequential? xs) (apply concat xs)
    :else (last xs)))

(defn escape-shell
  "the world's WORST shell escaper"
  [s]
  (str "'" (str/escape s {\' "'\\''"}) "'"))

(defn remove-parent
  "Given an file path, remove the first N directories.
   If N is not given, assume N=1."
  ([path] (if (= 1 (count (fs/components path))) path (remove-parent path 1)))
  ([path n] (->> path fs/components (drop n) (apply fs/path))))

(defn remove-ext
  [path]
  (first (fs/split-ext path)))

(defn strip-prefix
  [s pre]
  (let [quoted (java.util.regex.Pattern/quote pre)
        prefix (re-pattern (str "^" quoted))]
    (str/replace-first s prefix "")))

(defn strip-suffix [^String s ^String suffix]
  (if-not (.endsWith s suffix) s
    (.substring s 0 (- (count s) (count suffix)))))

; https://ask.clojure.org/index.php/14203/how-to-persist-clojure-data-structures-to-disk?show=14205#a14205
(defn write-edn [obj]
  (binding [*print-length* nil
            *print-level* nil
            *print-dup* false
            *print-meta* false
            *print-readably* true
            ;; namespaced maps not part of edn spec
            *print-namespace-maps* false]
    (let [mapped (walk/postwalk #(if (fs/path? %) (str %) %) obj)]
      (pr mapped))))
