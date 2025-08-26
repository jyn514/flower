(ns expressions.title
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [flower.utils :refer [remove-ext]]))

(defn- unslugify [filename]
  (if (= "index.html" (fs/file-name filename))
    (-> filename fs/parent fs/file-name)
    (-> filename remove-ext (str/replace #"-" " "))))

(defn title
  [post]
  (or (:title post)
      (-> post :path unslugify)))
      ; this is cursed and easily leads to dependency cycles
      ;(-> post :content meta/render (html/select "title") html/text)))
