(ns expressions.title
  (:require [flower.utils :refer [remove-ext]]
            [clojure.string :as str]))

(defn- unslugify [filename]
  (-> filename remove-ext (str/replace #"-" " ")))

(defn title
  [post]
  (or (-> post :frontmatter :title)
      (-> post :path unslugify)))
      ; this is cursed and easily leads to dependency cycles
      ;(-> post :content meta/render (html/select "title") html/text)))
