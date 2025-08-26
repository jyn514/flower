(ns expressions.pages 
  (:use flower.utils)
  (:require
   [babashka.fs :as fs]
   [java-time.api :as jt]
   [clojure.string :as str]
   [expressions.utils :refer [as-map split-all]]))

(defn- unslugify [filename]
  (let [name (if (= "index.html" (fs/file-name filename))
               (-> filename fs/parent fs/file-name)
               (-> filename remove-ext))]
    (str/replace name #"-" " ")))

(defn title
  [post]
  (or (:title post)
      (-> post :flower/source-file remove-parent unslugify)))
      ; this is cursed and easily leads to dependency cycles
      ;(-> post :content meta/render (html/select "title") html/text)))

(defn format-date [date format]
  (->> date jt/offset-date-time (jt/format format)))

(defn date
  ([post] (date post "yyyy-MM-dd"))
  ([post format]
   (when (nil? (:date post))
     (throw (ex-info (str "post " (:flower/source-file post) " is missing a date") {})))
   (format-date (:date post) format)))

(defn is-section [page]
  (= (fs/file-name (:flower/path page)) "index.html"))

(defn is-talk [page]
  (str/starts-with? (:flower/path page) "talks/"))

(defn is-hidden [post]
  (let [extra (:extra post)]
    (or
      (:unlisted extra)
      (:stub extra))))

(defn sort-by-date-descending [left right]
  (compare (:date right) (:date left)))

(defn categorize [all-pages]
  ; NOTE: order is important
  (let [[meta pages] (split-all :meta all-pages)
        [sections regular-pages] (split-all is-section pages)
        [talks posts] (split-all is-talk regular-pages)
        sorted-posts (sort sort-by-date-descending posts)
        [hidden-posts visible] (split-all is-hidden sorted-posts)
        [rss-only-posts main-posts] (split-all :rss_only visible)]
    (as-map meta talks sections hidden-posts rss-only-posts main-posts)))
