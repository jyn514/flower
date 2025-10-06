(ns transformers.toc
  "Replace <flower-toc> HTML tags with a table of contents." 
  (:require
   [clojure.string :as str]
   [expressions.html :refer [->element attrs replace-with! select tag
                             text-content]]
   [hiccup2.core :as h]))

(defn get-depth [header]
  (-> header tag (subs 1) parse-long))

(defn transform-elems
  [[html depth] [new-depth id content]]
  (loop [current-depth depth, current-html html]
    (cond
      (= new-depth current-depth)
      (let [new-html (if content
                       (str current-html (h/html [:li [:a {:href (str "#" id)} content]]))
                       current-html)]
        [new-html new-depth])

      ; TODO: this is dumb lol, do this structurally with Elements instead
      (> new-depth current-depth) (recur (inc current-depth) (str current-html  "<ul>"))
      (< new-depth current-depth) (recur (dec current-depth) (str current-html "</ul>")))))

(defn generate-toc [doc depth]
  (let [elems (for [d (range 1 (+ depth 2))] (str "h" d "[id]"))
        headers (select doc (str/join "," elems))
        summaries (for [h headers]
                    [(get-depth h) (-> h attrs :id) (text-content h)])]
    (first
      (reduce transform-elems ["" 0] (concat summaries [[0 nil nil]])))))

(def default-depth 2)

(defn transform
  [{:keys [content frontmatter]}]
  (if-not (= "html" (:flower/filetype frontmatter)) content
    (let [doc (->element content)]
      (doseq [toc (select doc "flower-toc")
              :let [unparsed (-> toc attrs :depth)
                    depth (or (parse-long unparsed)
                              (do (println "warning: unknown depth" (pr-str unparsed) "for <flower-toc>, assuming" default-depth)
                                  default-depth))]]
        (replace-with! toc (generate-toc doc depth)))
      (str doc))))
