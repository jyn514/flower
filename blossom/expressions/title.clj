(ns expressions.title
  (:require [flower.utils :refer [remove-ext inspect]]
            [clojure.string :as str]))

(defn- unslugify [filename]
  (-> filename remove-ext (str/replace #"-" " ")))

(defn title
  [post]
  (or (:title post)
      (-> post :flower/source-file unslugify)))
      ; this is cursed and easily leads to dependency cycles
      ;(-> post :content meta/render (html/select "title") html/text)))
