---
preprocessors: []
---
the smallest flower site is simply a markdown file with your content:
```
$ cat pages/look-ma-new-SSG.md
# look ma, new SSG!

i built a new SSG, and it works!
```
together with the default files `flower new` generates,
that generates a very basic scaffold with an index of pages and your rendered page.

you probably want to customize your site, though. a simple flower site could look like this:
```
$ tree
.
├── expressions
│   └── kbd.clj
├── pages
│   └── look-ma-new-site.md
├── transformers
│   └── title.clj
├── sass
│   └── minima.scss
├── static
│   └── favicon.jpg
└── templates
    ├── index.html
    └── page.html
```

### kinds of files
flower has four kinds of files:
- pages and templates
- transformers and expressions
- static files
- `build.clj` and `flower.edn`

it also comes with some default custom commands, such as compiling Sass to CSS and markdown to HTML.

let's look at them one at a time.
#### pages
"look-ma-new-site.md" is a "page", like most other SSGs. it can contain metadata as frontmatter:
```
---
description: how i built a new site using flower
---
# Look ma, new site!

flower is cool because it lets me choose my own footnote rendering!
```
metadata delimited with `---` is YAML. `+++` is TOML. `;;;` is JSON (SUBJECT TO CHANGE). `###` is [EDN].

[EDN]: https://clojuredocs.org/clojure.edn

##### preprocessing pages
preprocessors are hot-swappable. you can use any you like, such as handlebars, Hugo, or even the C preprocessor.

by default, flower enables a clojure preprocessor, which looks like this (credit [Scott Aaronson](https://scottaaronson.blog/?p=8088)):
```clojure
◊(def bb5 "47,176,870")

# BusyBeaver(5) is now known to be ◊bb5

Only now, after an additional 41 years, do we know the fifth Busy Beaver value.
Today, an international collaboration called bbchallenge is announcing that
it’s determined, and even formally verified using the Coq proof system, that
BB(5) is equal to ◊bb5—the value that’s been conjectured since 1990, when
Heiner Marxen and Jürgen Buntrock discovered a 5-state Turing machine that runs
for exactly ◊bb5 steps before halting, when started on a blank tape. The new
bbchallenge achievement is to prove that all 5-state Turing machines that run
for more steps than ◊bb5 actually run forever—or in other words, that ◊(bb5)
is the maximum finite number of steps for which any 5-state Turing machine can
run. That’s what it means for BB(5) to equal ◊bb5.
```
that replaces all instances of `◊bb5` with the string `47,176,870`.
see [Pollen: The lozenge](https://docs.racket-lang.org/pollen/pollen-command-syntax.html#%28part._the-lozenge%29) for how to type the escape character and background about how it was picked.
flower embeds a fully-featured clojure interpreter; see [Learn Clojure](https://clojure.org/guides/learn/clojure) for more information.

note that the first line uses `◊(def)` while the replacements use `◊bb5`, with no parens.
as a shortcut, `◊ident` can be used in place of `◊(print-str ident)`, as long as `ident` is a single clojure identifier.
`◊(bb5)` won't do what you expect—it tries to evaluate `bb5` as a function.

you can hide a template expression from the output with a normal HTML comment: `<!-- -->`.
but sometimes you may want to avoid evaluating it at all (e.g. if it gives an error you don't want to fix right now).
to avoid evaluating an expression, prefix it with `#_`, like a [normal clojure ignore](https://clojure.org/reference/reader#_dispatch):
`◊#_(this-function-does-not-exist)`.
this only works for lists—to comment out identifiers, use a line comment: `◊;this-var-does-not-exist`

##### reusable expressions

you may want to write reusable expressions for your pages (these are often called "shortcodes" or "macros" in other SSGs).
to do so, you write normal clojure.
here's an example function that transforms `◊(kbd ctrl+k f)` into `<kbd>ctrl + k</kbd><kbd>f</kbd>`:
<!-- TODO test all these examples -->
```clojure
◊(def sed clojure.string/replace)
◊(defn kbd [keys]
  (let [transformed
        (sed (sed keys " " "</kbd><kbd>") "+" " + ")]
    (fmt "<kbd>{transformed}</kbd>")))

my tmux prefix key is ◊(kbd "ctrl+k f").
```

##### clojure and java libraries

flower also embeds the [`hiccup`] library, with [`html`] in the default namespace, so we could have written that in a more functional style:

[`hiccup`]: https://github.com/weavejester/hiccup
[`html`]: https://weavejester.github.io/hiccup/hiccup2.core.html#var-html

```clojure
◊(defn kbd [keys]
  (str/join
    (map #(html [:kbd (sed % "+" " + ")])
         (str/split keys #" "))))
```
if you want to reuse this code between pages, place it in `expressions/kbd.clj`.
the directory and file extension are important, but the file name isn't; all .clj files in `expressions/` will be loaded.

for more information about the flower language, see [the language intro](docs/language.md).

##### index pages
often, you will want to make an index of pages in your site.
in flower, all pages have enough information to be an index page; flower itself doesn't have any concept of them.
the `pages` local variable has metadata about all pages in your site:
```clojure
<ul>◊(for [page pages
           :let [title (:title page)])«
  <li>◊title</li>
»</ul>
```
#### templates
above, we had a `description` metadata keys in our page. that metadata is not interpreted by flower itself, but by your templates. templates are any file in `templates/`.

here's a simple example of what `templates/page.html` could look like:
```html
<!DOCTYPE html><html>
<head>
  <title>the website of jyn</title>
  <meta name=description>◊(:description frontmatter)
</head>
<body>◊content</body>
</html>
```
note that this is exactly the same syntax as before, we just have a `page` local variable available to us now. see the API reference for a full list of locals injected by flower.

if you want to change which template is used for a single page, add `template: my-template.html` to the frontmatter, with the filename relative to the `templates` dir.

##### including templates

you may want to nest templates.
say that you have a footer that is used everywhere on your site, but you have two different templates for index pages and blog posts.
your directory layout could look something like this:
```
.
├── pages
│   ├── first-post.md
│   └── index.md
└── templates
    ├── footer.html
    ├── index.html
    └── post.html
```
to include `footer.html` in `index.html`, call the clojure function `include` with the name of the file you're including, relative to the `templates` directory:
```clojure
◊(include "footer.html")
```
`◊(include)` also works in pages. in fact, in flower, templates and pages are exactly the same.
the only difference is that pages are a "build root": they generate a URL in your final site.
templates are only interpreted if at least one page is embedded into them.

you may want to pass a custom context to a template (for example, an SVG that is a different color on different pages).
to do so, pass a map of the local variables you want to be available:
```clojure
◊(include "logo.svg" {'color "red"})
```
then, in `logo.svg`, `◊color` will expand to the text `red`.

<!-- TODO: figure out if `◊(include "../pages/first-post.md")` can or should work lol -->

##### embedding templates

sometimes, you may want to invert flow control: instead of including a template inside your current template, you want to embed your current template inside another template.
for example, your nesting chain could look like this: `my-post.md` inside `music-theory.html` inside `default.html`.
Zola and other template languages descended from Django call this "inheritance".
in flower, this is a clojure call with the names of the local variables you want to embed: `◊(embed "music-theory.html" {'body "this is *some* markdown"})`.
in fact, `include` is literally defined as `(defn include [filename] (embed filename {}))`.

most of the time, dealing with quoting, escaping, and formatting nested markup is a pain to deal with. to make this easier, flower offers an easy way to embed markup inside of clojure:
```clojure
◊(def body)«
  this is *some* markdown
»
◊(include "music-theory.html" {'body body})
```

### changing preprocessors

[TODO](https://codeberg.org/jyn514/flower/issues/55)

<!--
say you want to work with handlebars instead of clojure.
to do so, change your file metadata to name the handlebars preprocessor:
```
---
preprocessors: ["handlebars"]
---
```

this works for both pages and templates. 

#### advanced: custom preprocessors

TODO docs

sketch: write a transformer. you can call an external compiler with flower.unsafe/process. make sure to record your file dependencies with flower.reflect/read-file.
-->

### transformers
say that you want to have some HTML that is common across every generated page, regardless of what template it was generated from. flower allows you to transform generated pages using “post-processors”.
here is a sample post-processor, in a file named `transformers/title.clj`, which takes your `<h1>` tag and duplicates it into a `<title>` tag.
just like before, you write clojure, but unlike before, you define a function
named `transform` instead of embedding clojure in your content.
```clojure
(def transform [page]
  (let [content (:content page)
        title (:content (select content "h1"))]
    (append (html [:title title])
      (select "head" content))))
```
note that post-processors are *not* allowed to have frontmatter.
they work on each page, one at a time, and cannot be configured.
any configuration logic (such as transformer ordering) goes in your [`build.clj`][custom-build.md] code, not in the meta-build system.

[custom-build.md]: ./custom-build.md

### custom build tasks

flower allows you to write custom build commands that are integrated into the flower build process.
for example, this gets you live-reload for the commands, automatic rebuild detection if part of your site depends on the command, and progress messages that are integrated with a normal build.

See [custom-build.md] for more info.

