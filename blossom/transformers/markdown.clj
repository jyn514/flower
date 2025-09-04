; (ns transformers.markdown)
(require '[hiccup2.core :as hiccup]
         '[flower.utils :refer [inspect]]
         '[nextjournal.markdown :as md])

(declare renderers)

(defn render-chunk [ast]
  (md/->hiccup renderers ast))

; <footer class="footnotes">
; <ol class="footnotes-list">
; <li id="fn-1">
; <p> 
; <a href="#fr-3-1">↩
(defn trans-footnote [cx note]
  ; NOTE: we ignore :label for now
  (let [arrow [:span " " [:a {:href (str "#fr-" (:ref note))} "↩"]]
                  ; NOTE: doesn't allow multiple refs to the same footnote
        footnote (md/into-hiccup [:li {:id (str "fn-" (:ref note))}] cx note)]
    (update-in footnote [(dec (count footnote))] #(conj % arrow))))
(defn trans-footnote-ref [_cx node]
  [:sup.footnote-reference {:id (str "fr-" (:ref node))}
   [:a {:href (str "#fn-" (:ref node))} (:label node)]])

(def renderers 
  (assoc md/default-hiccup-renderers
         :footnote trans-footnote
         :footnote-ref trans-footnote-ref
         :html-inline (comp hiccup/raw md/node->text)
         :html-block (comp hiccup/raw md/node->text)))

(defn render-md [src]
  (let [parsed (md/parse src)
        footnotes (map render-chunk (:footnotes parsed))
        rendered (render-chunk parsed)
        footnote-wrapper [[:hr] [:footer.footnotes [:ol.footnotes-list footnotes]]]
        combined (if (seq footnotes) (into rendered footnote-wrapper) rendered)]
    (hiccup/html combined)))

(defn transform [{page :content meta :frontmatter :as args}]
  (if (= "md" (:flower/filetype meta))
    (let [new-meta (assoc meta :flower/filetype "html")]
      (assoc args
             :content (render-md page)
             :frontmatter new-meta))
    page))
