Common things you may want to do with flower:

Change the site title
------

Add `(def site-title "my-title")` to `expressions/constants.clj`.

Use a custom order for your pages
-----

In index.html (or wherever you put your index page), pass a custom comparator into `categorize`:
```clj
<ul class="post-list">◊◊(for [post (:main-posts (categorize pages custom-sort))])«
 <!-- ... -->
»</ul>
```

Here is a sample custom comparator which uses a hard-coded order:


Override only a single function from a clojure defaults file
--------

First, create a file with the same name (e.g. `expressions/pages.clj`).
Then, redefine all the vars from the default namespace into the current namespace:
```clj
(ns expressions.pages
  (:require defaults.expressions.pages))
(doseq [[sym var] (ns-publics 'defaults.expressions.pages)]
  (intern 'expressions.pages sym var))
```

