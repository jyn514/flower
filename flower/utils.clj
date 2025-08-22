(ns flower.utils
  (:use [flower.internal.utils])
  (:require [hiccup2.core :as hiccup]
            [sci.core :as sci]
            [nextjournal.markdown :as md]))

; NOTE: these helpers are exposed to all interpreted code,
; so they must not interact with the filesystem.

(defn md->html [md]
  ; https://github.com/nextjournal/markdown?tab=readme-ov-file#html-blocks-and-html-inlines
  (let [renderers (assoc md/default-hiccup-renderers
                         :html-inline (comp hiccup/raw md/node->text)
                         :html-block (comp hiccup/raw md/node->text))]
  (->> md (md/->hiccup renderers) hiccup/html str)))

; (reexport inspect strip-prefix merge-deep)

(def utils-ns (sci/create-ns 'flower.utils))
(def bindings
  {'inspect inspect
   'strip-prefix strip-prefix
   'remove-parent remove-parent
   'remove-ext remove-ext
   'merge-deep merge-deep
   'escape-ninja escape-ninja
   'join-ninja join-ninja
   'escape-shell escape-shell
   'fmt (sci/copy-var fmt utils-ns)
   'md->html md->html})


