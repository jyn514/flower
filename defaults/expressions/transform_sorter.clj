(ns expressions.transform-sorter 
  (:require
   [flower.fs :as fs]))

(def ^:private transss "trans sorter start"
  ["preprocess" "markdown" "embed" "highlight"])

(defn- trans-order [p]
  (let [i (.indexOf transss p)]
    (if (= -1 i) nil i)))

; -1 means left comes first, 1 means right comes first
(defn trans-sorter [left right]
  (let [[left right] (map #(-> % fs/file-name fs/strip-ext) [left right])
        [lscore rscore :as scores] (map trans-order [left right])]
    (cond
      ; content always comes last
      (= "content" left) 1
      (= "content" right) -1
      ; if both have a defined order, compare them
      (every? some? scores) (apply compare scores)
      ; otherwise, transformers with a defined order come first
      (some? lscore) -1
      (some? rscore) 1
      ; otherwise, alphabetical
      :else (compare left right))))

