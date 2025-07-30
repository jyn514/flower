# flower: a meta-SSG based on [pollen] and [hakyll]

[pollen]: https://docs.racket-lang.org/pollen
[hakyll]: https://jaspervdj.be/hakyll/

**NOTE: still in pre-alpha, blog post forthcoming**

## features

all the basics:

- static binaries
- syntax highlighting
- live-reload
- RSS feed support

and some weird ones:

- support for arbitrary build commands
- import your existing site; no changes to templates or content needed to serve the same site (some amount of configuration necessary)
- default templating language is a fully-featured programming environment (clojure). the same language is used in pages and templates, with very few restrictions.
- post-process generated HTML based on CSS selectors. for example, create your own table of contents, or parse the `<title>` tag out of the pages headings.
- choose your own language. you are not tied to the built-in template language; you can even use two different languages for the inline preprocessing and your templates.
- render individual files at a time. this allows you to wrap flower in an external build system and reuse its caching.

## why a new SSG?

because all the others are a pain to use.
- jekyll requires fumbling through the ruby dependency ecosystem
- hakyll requires you to learn haskell, and requires fumbling through the haskell dependency ecosystem
- zola sharply restricts what you can do with templates (the whole phase system is weird and requires lots of workarounds)
- hugo requires you to learn the weird Go templating system
- pollen is mostly unmaintained
- "just hack something together yourself" distracts you from actually writing your blog posts, and doesn't get you nice things like hot-reload and an RSS feed

flower is for people who just want to build a site with a minimum of fuss, but still have a gentle "on-ramp" to doing more complicated things in the future.

flower is meant to be something you fork and embed in your own repository. you can download pre-built binaries, but you can also just as easily have your own copy of the source. there is no pressure to update unless you need a bug fix.
## overview
- five phases
	- build dependency graph
	- preprocessing
	- template embedding
	- custom commands (including built-in commands)
	- post processing
- four kinds of files
	- pages
	- templates
	- expressions
	- static files
## guide
the smallest flower site is simply a markdown file with your content:
```
$ cat pages/look-ma-new-SSG.md
# look ma, new SSG!

i built a new SSG, and it works!
```
that generates a very basic scaffold with an index of pages and your rendered writing.

you probably want to customize your site, though. a simple flower site could look like this:
```
$ tree
.
├── expressions
│   └── kbd.clj
├── pages
│   ├── look-ma-new-site.md
│   └── page.html
├── postprocessors
│   └── title.clj
├── sass
│   └── minima.scss
├── static
│   └── favicon.jpg
└── templates
    └── index.html
```

### kinds of files
flower has four kinds of files:
- pages
- templates
- expressions
- static files

it also comes with some built-in custom commands, such as compiling Sass to CSS and markdown to HTML.

let's look at them one at a time.
#### pages
"look-ma-new-site.md" is a "page", like most other SSGs. it can contain metadata as frontmatter:
```
---
description: how i built a new site using flower
---
# Look ma, new site!

flower is cool because it lets me choose my own syntax highlighter!
```

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

note that the first line uses `◊(def)` while the replacements use `◊bb5`, with no parens. as a shortcut, `◊ident` can be used in place of `◊(print-str ident)`, as long as `ident` is a single clojure identifier. `◊(bb5)` won't do what you expect—it tries to evaluate `bb5` as a function.

you may want to write your own expressions for your pages (these are often called "shortcodes" or "macros" in other SSGs). to do so, you write normal clojure. here's an example function that transforms `◊(kbd ctrl+k f)` into `<kbd>ctrl + k</kbd><kbd>f</kbd>`:
<!-- TODO test all these examples -->
```clojure
◊(def sed clojure.string/replace)
◊(defn kbd [keys]
  (let [transformed
        (sed (sed keys " " "</kbd><kbd>") "+" " + ")]
    (fmt "<kbd>{transformed}</kbd>")))

my tmux prefix key is ◊(kbd "ctrl+k f").
```
flower also embeds the [`hiccup`] library, with [`html`] in the default namespace, so we could have written that in a more functional style:

[`hiccup`]: https://github.com/weavejester/hiccup
[`html`]: https://weavejester.github.io/hiccup/hiccup2.core.html#var-html

```clojure
◊(defn kbd [keys]
  (str/join ""
    (map #(html [:kbd (sed % "+" " + ")])
         (str/split keys #" "))))
```
if you want to reuse this code between pages, place it in `expressions/kbd.clj`. the directory and file extension are important, but the file name isn't; all .clj files in `expressions/` will be loaded.
##### index pages
often, you will want to make an index of pages in your site.
to do so, first mark your page as an index. then, simply generate your pages using the `pages` local variable.
<!-- https://medium.com/code-is-data-data-is-code/python-f-string-like-string-interpolation-in-clojure-385015ab2dc2 -->
```clojure
---
index = true
---
<ul>◊(for [page pages]
	(let [title (:title page)] (fmt "<li>{title}</li>")))</ul>
```
other than the `pages` local, index pages are just normal pages, which means you can combine them with templates like normal.
#### templates
above, we had a `description` metadata keys in our page. that metadata is *not* interpreted by flower itself, but by your templates. templates are any file in `templates/`.

here's a simple example of what `templates/page.html` could look like:
```html
<!DOCTYPE html><html>
<head>
  <title>the website of jyn</title>
  <meta name=description>◊(:description page)
</head>
<body>◊(:content page)</body>
</html>
```
note that this is exactly the same syntax as before, we just have a `page` local variable available to us now. see the API reference for a full list of locals injected by flower.

if you want to change which template is used for a single page, add `template = my-template.html.clj` to the frontmatter, with the filename relative to the `lib` dir.


<!--
by default, this requires rebuilding your page whenever any page in your site changes.
to rebuild less frequently, say which files you want to index by defining a `depends` variable:
```clojure
◊(def depends "*") <!-- allows any file glob -->
<!-- or --
◊(def depends ["first-page.html", "second-page.html"]) <!-- takes a list of file paths, relative to "pages" -->
<!-- or --
# https://jsoup.org/
<!-- takes an arbitrary clojure function.
     note that at this stage of processing, `(:content page)` is  an empty string. --
◊(def depends (fn [page] (contains? (:metadata page) "author")))
```
-->
<!-- not well motivated; suggest `template = ""` instead?
#### preprocessed files
so far we have been working with pages and templates.
but these are not the only options available to us.
say you have a 404 page that looks very different than the rest of your site.
you could create a `templates/404.html.clj` and a corresponding `pages/404.md`.
but that's a little silly, right? if would be nice if you could just generate the file directly.

with flower, you can. if you name your file `pages/404.md.clj`, it will automatically be preprocessed, but without injecting a template before or after the content.
then, it will be rendered as markdown to HTML.

if you just want to write raw HTML, no problem. just name your file `pages/404.html.clj` instead.
-->

### changing preprocessors

say you want to work with handlebars instead of clojure.
to do so, change your file metadata to name the handlebars preprocessor:
```
+++
preprocessors = ["handlebars"]
+++
```

this works for both pages and templates.

#### advanced: custom preprocessors

TODO

sketch: JSON input on stdin, JSON output on stdout, you can do whatever you like in the middle

### postprocessors
say that you want to have some HTML that is common across every generated page, regardless of what template it was generated from. flower allows you to transform generated pages using “post-processors”.
here is a sample post-processor, in a file named `postprocessors/title.clj`, which takes your `<h1>` tag and duplicates it into a `<title>` tag.
just like before, you write clojure, but unlike before, you define a function
named `transform` instead of embedding clojure in your content.
```clojure
(def transform [page]
  (let [title (:content (select page "h1"))]
    (append (html [:title title])
      (select "head"))))
```
note that post-processors are *not* allowed to have frontmatter.
they work on each page, one at a time, and cannot be configured.
any configuration logic (such as postprocessor ordering) goes in your code, not in the meta-build system.

<!--
the only configuration allowed is to tell the build system which files are post-processors.
by default all files in `lib/postprocess*.clj` are postprocessors.
you can change this by putting e.g. `postprocessors = "lib/postprocessors/*` in `config.toml` in the root directory.
the string is a libc-style file glob.
-->

### custom build tasks
say you are building a demo of a Rust program that compiles to WASM and runs in the browser. you want to integrate that build with the build of your site.

rather than running many commands in sequence (and losing live-reload as a result), you can tell flower to build the program for you.

TODO: design and document `build.clj`
<!--
you do so by writing a [`lib/rules.ninja` file](https://ninja-build.org/manual.html#_writing_your_own_ninja_files).
here's an example file that could build that rust program for us:
```ninja
rule wasm
  command = cd my-project && cargo build --release --target wasm32-wasip1

# incomplete dependency list; use https://github.com/declantsien/cargo-ninja for something more accurate
build my-project/target/release/my-bin: wasm my-project/pages/main.rs my-project/Cargo.toml

build public/static/my-bin: link my-project/target/release/my-bin
```
`link` is a pre-defined rule in flower's standard library. all other commands are documented in the ninja manual.

#### advanced: generating `rules.ninja` with a custom command

TODO
-->

### importing existing sites
we now know enough to see how to import an existing static site with an existing build process.
the basic idea is to write a custom build task that "mounts" the existing site at a specific URL.
you can mount anywhere (including the site root!) as long as you do not conflict with any other files generated by flower.

<!--this process is common enough that there is a custom wrapper for it.
the wrapper will automatically check for conflicts and error if it detects any.
otherwise, it will generate the `rules.ninja` file for you, with dependency tracking.
to use it, put your code in `build.clj` instead of `lib/rules.ninja`:
-->
```clojure
(defn build-graph
	[writer]
	(flower.writer/custom writer "cd my-zola-site && zola build")
	(flower.writer/mount writer "my-zola-site/public" "/zola"))
```

## reference
pollen operates in four phases. within a phase, ordering is not specified (for example, postprocessing steps should not depend on the output of a previous step).
if you need detailed ordering constraints, put them in your clojure code.
1. scan all files to determine their dependencies.
	- run `build.clj`.
	- parse frontmatter.
	- mark pages as depending on their templates, index pages as depending on all other pages, temporary files as depending on their preprocessed files, and so on.
	- generate a build.ninja.
	- NOTE: autoloaded files (e.g. `expressions/*.clj`) must be static files, not generated by preprocessed files.
2. run all preprocessing steps recursively.
	- all "explicit" preprocessing steps (i.e. specified via file extensions) are run
	- all files in `pages/` are semantically preprocessing files unless they have explicitly opted out with `preprocessors = []`
3. embed pages into templates. then, run all "static" transforms (e.g. `.md` -> `.html`)
	- embedding semantically happens after preprocessing the page but before converting the markdown to HTML (so you can e.g. have a markdown list that starts in a template but ends in a page).
4. run all post-processing steps.
	- post-processing steps must never depend on a list of all files (since that would prevent us from statically constructing build.ninja).
	- post-processing steps must not change the filename.
	- post-processing steps must not depend on the order in which they are run.

### limitations
NOTE: embedded clojure can write to stderr like normal.
but it cannot write to stdout, because that's used by flower for internal communication.
attempts to do so will be redirected to stderr.

---
# FAQ

## why ninja?

several reasons:
- ninja exposes reflection capabilities via `ninja query`, allowing flower to get a list of output files that is based on build.ninja, without having to calculate dependency graphs itself.
- ninja is concise, fully general, and not tied to a specific language ecosystem.
- separating rules (edges) from dependencies (nodes) allows extending it at runtime with custom build rules.
- the language is sharply and intentionally restricted. put your complicated trickery in your templates and meta-build code, not in the build system.

## why clojure?

well, let's start with:
### why LISP?
and the answer there is very simple: LISPs syntax makes it very easy to embed inline. the only alternative i know in this space is handlebars/jinja, which could have been *actively* designed to discourage abstraction and code reuse.

there are some alternatives that are meant for embedding, at the cost of being harder to parse, like Lua/Rhai/TCL.
but they basically aren't possible to compile to static binaries, which means that the SSG itself has to be written in a different language.
flower is intentionally one language all the way through, to make it easier to modify the core without needing to learn new tools.
### ok but really why clojure
clojure runs on the JVM, which means:
- you get any dependencies you want,
- most importantly, you get GraalVM, which gives you native binaries and extremely fast startup times. no need to install the JRE or the racket compiler; the bundled executable comes with everything you need.
## why a "meta-build" system instead of something simpler?
because if i'm going to be insane enough to write my own SSG, i want it to be one that i don't rip up and throw away in a year. that means it has to be extensible *and* not break *and* be easy enough to import that i don't spend a bunch of time rewriting things away from jinja again.

# security
it's an SSG. it's running arbitrary code because you (or i) wrote all the code. don't treat it as a security boundary and you'll be fine.

the built-in web server is probably not resilient to any kind of malicious use. only use it for dev when you want live-reload. use a real web server (e.g. Caddy) to serve things in prod and you'll be fine.
