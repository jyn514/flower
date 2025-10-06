(ns transformers.md-html-links 
  "Transform relative .md links in pages into .html links."
  (:require
   [clojure.string :as str]
   [expressions.html :refer :all]
   [expressions.utils :refer [inspect remove-parent]]
   [flower.locals :refer [pages]])
  (:import
   [java.net URI URISyntaxException URLEncoder]
   java.nio.charset.StandardCharsets))

(defn with-path [uri new-path]
  (URI.
    (.getScheme uri)
    (.getUserInfo uri)
    (.getHost uri)
    (.getPort uri)
    new-path
    (.getQuery uri)
    (.getFragment uri)))

(defn encode [path]
   (URLEncoder/encode path (StandardCharsets/UTF_8)))

(defn normalize [rel all-srcs]
  (let [parsed (try (URI. rel) (catch URISyntaxException _ (URI. (encode rel))))
        normalized (some-> parsed .getPath (str/replace #"^\./" ""))]
    (when (and (not (.getScheme parsed))
               (str/ends-with? normalized ".md")
               (some #{normalized} all-srcs))
      (str (with-path parsed
             (str/replace normalized #"\.md" ".html"))))))

(defn transform [{:keys [content frontmatter]}]
  (if-not (-> frontmatter :flower/filetype (= "html")) content
    (let [doc (->element content)
          get-src #(-> % :flower/source-file remove-parent str)
          all-srcs (map get-src pages)]
      (doseq [a (select doc "a")
              :let [dst (-> a attrs :href (normalize all-srcs))]
              :when dst]
        (set-attr! a :href dst))
      (str doc))))
