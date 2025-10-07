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

<!-- ## What is flower? -->
<!---->
<!-- Flower is a [static site generator](https://en.wikipedia.org/wiki/Static_site_generator) that is a library, not a framework. -->
<!-- It comes with good defaults that allow you to get started quickly with minimum boilerplate, but scales to projects of great size and complexity without having to rewrite your code. It is extensible, pluggable, and extremely configurable—because all the code is exposed to you the creator. -->
<!---->
<!-- Flower’s guiding principles are: -->
<!-- 1. [It’s your site, you should control what’s on it](#what-does-flower-do-differently?). -->
<!-- 2. [Power comes from structure, not expressiveness](https://buttondown.com/hillelwayne/archive/the-capability-tractability-tradeoff/). -->
<!-- 3. Prefer composing tools to monoliths. -->
<!-- 4. [Make the obvious thing the correct one](./language.md#filesystem-api). -->
<!---->
<!-- ## What does flower do differently? -->
<!---->
<!-- Nearly every part of flower is built in "user-space". -->
<!-- What that means is that you can override it. -->
<!-- For example, if you want to override how markdown footnotes are rendered, you could write something like this: -->
<!-- ```clj -->
<!-- ; transformers/markdown.clj -->
<!-- (ns transformers.markdown -->
<!--   (:require [expressions.utils :refer [reexport]] -->
<!--             [nextjournal.markdown :as md])) -->
<!-- (reexport 'defaults.transformers.markdown 'transformers.markdown) -->
<!---->
<!-- (defn footnote [cx note] -->
<!--     (md/into-hiccup [:li {:id (str "footnote-" (:ref note))}] cx note)) -->
<!---->
<!-- (def renderers  -->
<!--   (assoc defaults.transformers.markdown -->
<!--          :footnote trans-footnote)) -->
<!-- ``` -->
<!---->
<!-- If you want to add a custom syntax highlighter, you can do that too (see `.build/defaults/transformers/highlight.clj` for an example). -->
<!---->
<!-- If you want to have more categories than "posts", you don't need first-class support: -->
<!-- every page on a flower site has access to a `pages` metadata struct: -->
<!-- ```html -->
<!-- <!-- pages/index.html -->
<!-- <ul>◊◊(for [post pages -->
<!--            :when (:my-tag post)])« -->
<!--   <li><a href="◊◊(:flower/path post)">(:title post)</a></li> -->
<!-- »</ul> -->
<!-- ``` -->
<!---->
<!-- Template syntax is unified between pages and templates; the only difference is that templates don't get their own URL on the site. -->
<!---->
<!-- If you want to reuse code, you can write normal clojure and put it in `expressions/`. -->
<!-- If you don't like clojure as the template language and want to use Jinja, you can set a custom preprocessor. -->
<!-- If you don't know clojure, but still want to write complicated expressions, you can set a custom "runner" that lets you write Python in your `expressions`. -->
<!-- If you don't like markdown and want to use AsciiDoc, you can set a custom renderer. -->
<!---->
<!-- Every part of the site is designed to live in "user-space". -->
<!-- Very very few parts are "reserved" for me as the author of flower; -->
<!-- almost all of them can be implemented by you as the author of your own site. -->
