---
preprocessors: []
---
Common things you may want to do with flower:

Change the site title
------

Add `(def site-title "my-title")` to `expressions/constants.clj`.

Use a custom order for your pages
------------------

In index.html (or wherever you put your index page), pass a custom sorter into `categorize`:
```clj
<ul class="post-list">◊(for [post (:main-posts (categorize pages custom-sort))])«
 <!-- ... -->
»</ul>
```

Here is a sample custom comparator which uses a hard-coded order:
```clj
◊(require '[expressions.pages :refer [categorize sort-by-constant-name]])
◊(def page-order ["quickstart" "syntax" "language"])
◊(categorize pages #(sort-by-constant-name % page-order))
```

Rewrite an HTML page
-------
Flower currently exposes a stateful API for HTML transformations, which means you need an object reference to hold on to.
Once you have an object reference, you can modify it with CSS selectors.
```clj
(ns transformers.add-class
  (:require [expressions.html :refer [->element select add-class!]]))
(defn transform [{:keys [:content]}]
  (let [doc (->element page)]
    (doseq [anchor (select doc "a")]
      (add-class! anchor "my-anchor-class"))
    (str doc)))
```
Make sure to call `str` at the end.
Otherwise, you'll pass through a parsed HTML document, which will likely crash the next transformer that runs if it expects a string.

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

