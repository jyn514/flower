(require '(hiccup2 [core :as h]))
; (defn transform [page] (println "hiiii") (h/html [:h1 "TITLE"]))
(defn transform [page]
  (let [title (:content (select page "h1"))]
    (append (html [:title title])
      (select "head"))))
