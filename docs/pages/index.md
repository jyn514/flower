---
title: null
---
◊(require
  '[flower.fs :as fs]
  '[nextjournal.markdown :as md]
  '[transformers.markdown :refer [render-md default-renderers]]
  '[expressions.meta :refer [include]]
  '[expressions.utils :refer [remove-parent]]
  '[expressions.pages :refer [categorize title sort-by-constant-name]])

◊(def readme (include "../../README.md" {:preprocessors []}))
◊(defn rewrite-link [href]
  (let [normalized (fs/normalize href)
        docs-page (->> normalized fs/components (take 2) (apply fs/path) str (= "docs/pages"))]
    (if docs-page
      (remove-parent normalized 2)
      (if (= "CONTRIBUTING.md" (str normalized))
        "contributing.html"
        href))))
◊(defn render-link [cx {:as node :keys [attrs]}]
    (md/into-hiccup [:a {:href (rewrite-link (:href attrs))}] cx node))
◊(def renderers (merge default-renderers {:link render-link}))
◊(render-md readme renderers)

## Learn more about flower

◊(def page-order
    ["quickstart" "syntax" "language" "overlay" "guide/cookbook" "guide" "architecture" "custom-build" "contributing"])

<nav class="home">
  <ul class="post-list">◊(for [post (:main-posts (categorize pages #(sort-by-constant-name % page-order)))])«
  <li>
    <h2>
      ◊(html [:a {:class "post-link" :href (:flower/path post)} (title post)])
    </h2>
  </li>
  »</ul>
  ◊(if (fs/exists? "pages/atom.xml"))«
    <p class="rss-subscribe">subscribe <a type="application/atom+xml" href="/atom.xml">via RSS</a></p>
  »
</nav>
