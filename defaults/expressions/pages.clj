(ns expressions.pages 
  (:require
   [clojure.string :as str]
   [expressions.utils :refer [as-map remove-ext remove-parent
                              split-all strip-suffix]]
   [flower.fs :as fs]
   [java-time.api :as jt]))

(defn- unslugify [filename]
  (let [name (if (= "index.html" (fs/file-name filename))
               (-> filename fs/parent (or "index") fs/file-name)
               (-> filename remove-ext))]
    (str/replace name #"-" " ")))

(defn title
  [post]
  (get post :title
       (->> post :flower/source-file fs/file-name unslugify)))
      ; this is cursed and easily leads to dependency cycles
      ;(-> post :content meta/render (html/select "title") html/text)))

(defn parse-date [d]
  (try (jt/offset-date-time d)
    (catch clojure.lang.ExceptionInfo e
      ; HACK: SCI bug, `java.time.OffsetDateTime` is a symbol instead of a class
      (if (->> e ex-data :to pr-str (= "java.time.OffsetDateTime"))
        (jt/local-date d)
        (throw e)))))

(defn format-date [date format]
  (->> date parse-date (jt/format format)))

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

(defn- keyfn "for use with sort-by-constant-name"
  [page-order]
  (fn [page]
    (-> page :flower/source-file
        remove-parent str (strip-suffix ".md") (strip-suffix ".html")
        (as-> $ (.indexOf page-order $)))))

(defn sort-by-constant-name [pages page-order]
    (sort-by (keyfn page-order) pages))

(defn categorize
  ([all-pages] (categorize all-pages #(sort sort-by-date-descending %)))
  ([all-pages sorter]
   ; NOTE: order is important
   (let [[sections regular-pages] (split-all is-section all-pages)
         sorted-pages (sorter regular-pages)
         [talks all-posts] (split-all is-talk sorted-pages)
         [meta posts] (split-all :meta all-posts)
         [hidden-posts visible] (split-all is-hidden posts)
         [rss-only-posts main-posts] (split-all :rss_only visible)]
     (as-map meta talks sections hidden-posts rss-only-posts main-posts))))
