# flower: a meta-SSG based on [pollen] and [soupault]

[pollen]: https://docs.racket-lang.org/pollen
[soupault]: https://soupault.app/

**NOTE: still in pre-alpha, blog post forthcoming**

`flower` is currently EXTREMELY ROUGH. this is mostly on github so i can show it to people and as a tech demo.

## testimonials

folks the reviews for my new ssg are in

> I hate that this is exciting to me. This makes me want to write software.  
—burned out professional programmer

> you have made a tool i might actually use.  
—girl who rolls her own crypto

## features

all the basics:

- static binaries
- syntax highlighting -- TODO
- live-reload
- RSS feed support -- TODO

and some weird ones:

- support for arbitrary build commands
- import your existing site; no changes to templates or content needed to serve the same site (some amount of configuration necessary)
- default templating language is a fully-featured programming environment (clojure). the same language is used in pages and templates, with very few restrictions.
- post-process generated HTML based on CSS selectors. for example, create your own table of contents, or parse the `<title>` tag out of the pages headings. -- VERY WIP
- choose your own language. you are not tied to the built-in template language; you can even use two different languages for the inline preprocessing and your templates. -- NOT DOCUMENTED
- render individual files at a time. this allows you to wrap flower in an external build system and reuse its caching.

## quick start

### [install ninja](https://github.com/ninja-build/ninja/wiki/Pre-built-Ninja-packages)

### install the flower binary

two options:
#### download a static binary
1. go to https://github.com/jyn514/flower/actions?query=event%3Apush+branch%3Adev+is%3Asuccess
2. click on the latest successful action
3. scroll down to "Artifacts"

#### build from source
1. [install clojure](https://clojure.org/guides/install_clojure)
2. [install GraalVM](https://www.graalvm.org/downloads/)
3. `git clone https://github.com/jyn514/flower`
4. `cd flower`
5. `clojure -T:build native`

this will output a binary into `target/flower`.
put it somewhere in PATH.
make sure to use the binary, not the jar file - the jar is slow to start and will make your site rebuilds very slow.

### create your site

1. `mkdir my-site`
2. `flower new`
3. `flower watch`

`flower new` generates the skeleton of a site in the current directory.
feel free to edit any files it generates.

## overview
four phases:
1. build dependency graph
1. preprocessing
1. custom commands (including built-in commands)
1. post processing (including template embedding)
## guide
the smallest flower site is simply a markdown file with your content:
```
$ cat pages/look-ma-new-SSG.md
# look ma, new SSG!

i built a new SSG, and it works!
```
together with the other files `flower new` generates,
that generates a very basic scaffold with an index of pages and your rendered page.

you probably want to customize your site, though. a simple flower site could look like this:
```
$ tree
.
├── build.clj
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
- pages
- templates
- expressions
- static files

it also comes with some default custom commands, such as compiling Sass to CSS (TODO) and markdown to HTML.

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
TODO: this currently only works for lists, not idents, i.e. `◊#_ident` will give a syntax error.

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
##### index pages
often, you will want to make an index of pages in your site.
to do so, first mark your page as an index. then, simply generate your pages using the `pages` local variable.
<!-- https://medium.com/code-is-data-data-is-code/python-f-string-like-string-interpolation-in-clojure-385015ab2dc2 -->
```clojure
---
index: true
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
`◊(include)` also works in pages. i am not sure why you'd want that, but if you have a use case, please tell me :)

you may want to pass a custom context to a template (for example, an SVG that is a different color on different pages).
to do so, use `:locals`:
```clojure
◊(include "logo.svg" :locals {'color "red"})
```
then, in `logo.svg`, `◊color` will expand to the text `"red"`.

<!-- TODO: figure out if `◊(include "../pages/first-post.md")` can or should work lol -->

##### embedding templates

sometimes, you may want to invert flow control: instead of including a template inside your current template, you want to embed your current template inside another template.
for example, your nesting chain could look like this: `my-post.md` inside `music-theory.html` inside `default.html`.
Zola and other template languages descended from Django call this "inheritance".

for simple cases where you are embedding the whole template at once, you can write `template: default.html` in the frontmatter of `music-theory.html`, just like in a page.

you may want to embed only parts of the template.
to do this in flower, surround the content you want to embed in a `flower-embed` HTML tag, like you are calling a JSX component.
`flower-embed` takes two attributes, `template` and `name`.
`template` will be expanded as-if you had called `◊(include template :locals {name content})`, where `content` is all nested HTML inside the tag.

here is an example:
```html
<!-- in pages/my-post.md -->
---
template: music-theory.html
---

playing 8 white keys in a row on a piano gives you a diatonic scale.

<!-- in templates/music-theory.html -->
<flower-embed template=default.html name=body>
	<nav>
		<h4>tags</h4>
		<ul>
			<li><a href=/tags/music>music</a></li>
		</ul>
	</nav>
	◊content
</flower-embed>

<!-- in default.html -->
<html>
	<body>
		◊body
	</body>
</html>
```

i recommend using embedding sparingly.
it's very flexible, but makes logic non-local, and can be hard to follow if you step away from your site for a few months and come back to it later.

TODO: document somewhere that this ends up being expanded by a transformer

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
---
preprocessors: ["handlebars"]
---
```

this works for both pages and templates. TODO

#### advanced: custom preprocessors

TODO docs

sketch: JSON input on stdin, JSON output on stdout, you can do whatever you like in the middle. record your file dependencies in the JSON output.

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
any configuration logic (such as transformer ordering) goes in your code, not in the meta-build system.

TODO: this example works but API for even slightly more complicated things is not implemented

<!--
the only configuration allowed is to tell the build system which files are post-processors.
by default all files in `lib/transform*.clj` are transformers.
you can change this by putting e.g. `transformers = "lib/transformers/*` in `config.toml` in the root directory.
the string is a libc-style file glob.
-->

### custom build tasks
say you are building a demo of a Rust program that compiles to WASM and runs in the browser. you want to integrate that build with the build of your site.

rather than running many commands in sequence, you can tell flower to build the program for you.
create a `build.clj` that passes a map of your commands to `flower.build/generate`:
```clojure
(def my-exe "my-rust-program/target/release/my-bin")
(def commands
  {:rules
   [{:name "cargo"
     :command "cd my-rust-program && cargo build --release --target wasm32-wasip1"
     :description "compile my rust program to WASM"}]
   :builds
   [{:rule "cargo"
     :outputs my-exe
     ; walkdir respects .gitignore
     ; this overestimates; use https://github.com/declantsien/cargo-ninja for something more accurate
     :inputs (walkdir "my-rust-program")}]})

(build/generate commands (build/link my-exe "public/my-bin"))
```

`link` is a pre-defined rule in flower's standard library. all other commands are documented in [the ninja manual].

[the ninja manual]: https://ninja-build.org/manual.html#_writing_your_own_ninja_files

#### constraints

note that we had to explicitly name all the inputs to our program.
this is an intentional restriction, because it allows flower to know when your files need to be rebuilt.
as you'll see in a second, we need to name not just all *inputs*, but all *outputs*.
this allows flower to pass a list of your files to your index pages.

you may not care about including your existing site in your index pages; for example if your index pages are generated by your existing site.
in that case, you can opt-out by simply running `your-build-command && flower build`, i.e. move the work outside of flower.
live-reload will still work (even for files not created by flower!), and flower will never delete any path it did not create itself.
*however*, flower cannot know when a previous build command conflicts with its own output files, so you'll have to verify that yourself.

#### importing existing sites
we now know enough to see how to import an existing static site with an existing build process.
the basic idea is to write a custom build task that "mounts" the existing site at a specific URL.
you can mount anywhere (including the site root!) as long as you do not conflict with any other files generated by flower.
flower will automatically check for conflicts when you call `generate`.
```clojure
(def build-zola
  {:rules
   [{:name "zola"
     :command "cd zola && zola build"
     :description "build my existing zola site"}]})

; TODO: all this sucks!! idk how to make it better though
(def zola-dirs ["zola/static" "zola/content"]) ; need to watch these for newly created files
(def zola-markdown-posts
  (fs/glob "zola/content" "**.md"))
(def zola-html-posts
  (map #(->> % (build/with-ext "html") build/remove-parent (fs/path "public"))
       zola-markdown-posts))
(def zola-static
  (map #(fs/path "public" %)
       (fs/glob "zola/static" "**")))
(def all-zola-files (conj zola-html-posts zola-static zola-dirs))

(def mount-zola (build/mount "public" all-zola-files))

(build/generate build-zola mount-zola)
```

## reference
pollen operates in four phases. within a phase, ordering is not specified (for example, transforming steps should not depend on the output of a previous step).
if you need detailed ordering constraints, put them in your clojure code.
1. scan all files to determine their dependencies.
	- run `build.clj`.
	- parse frontmatter.
	- mark pages as depending on their templates, index pages as depending on all other pages, temporary files as depending on their preprocessed files, and so on.
	- generate a build.ninja.
	- NOTE: autoloaded files (e.g. `expressions/*.clj`) must be static files, not generated by preprocessed files.
2. run all preprocessing steps recursively.
	- all "explicit" preprocessing steps (i.e. specified via file extensions) are run
	- all files in `pages/` are semantically preprocessing files unless they have explicitly opted out with `preprocessors: []`
3. embed pages into templates. then, run all "static" transforms (e.g. `.md` -> `.html`)
	- embedding semantically happens after preprocessing the page but before converting the markdown to HTML (so you can e.g. have a markdown list that starts in a template but ends in a page).
  - embedding is implemented as a post-processor; it's listed as a separate phase because it runs before all other transformers.
4. run all post-processing steps.
	- post-processing steps must never depend on a list of all files (since that would prevent us from statically constructing build.ninja).
	- post-processing steps must not change the filename.
	- post-processing steps must not depend on the order in which they are run.

### limitations
NOTE: embedded clojure can write to stderr like normal.
but it cannot write to stdout, because that's used by flower for internal communication.
attempts to do so will be redirected to stderr.

`build.clj` must generate a static list of all inputs and outputs; see above for reasoning and workarounds.

`build.clj`, custom preprocessors, transformers, and templates must not write to the filesystem.
transformers must not read from the filesystem.
preprocessors and templates can read from the filesystem only if they list the files read in a `dependencies` JSON field.
this sounds more restrictive than it is; in practice you get all the info you need on stdin.

---
# FAQ

## why a new static site generator?

because all the others are a pain to use.
- jekyll requires fumbling through the ruby dependency ecosystem
- hakyll requires you to learn haskell, and requires fumbling through the haskell dependency ecosystem
- zola sharply restricts what you can do with templates (the whole phase system is weird and requires lots of workarounds)
- hugo requires you to learn the weird Go templating system
- pollen is mostly unmaintained
- "just hack something together yourself" distracts you from actually writing your blog posts, and doesn't get you nice things like hot-reload and an RSS feed

flower is for people who just want to build a site with a minimum of fuss, but still have a gentle "on-ramp" to doing more complicated things in the future.

additionally, flower is meant to be a demonstration of what it looks like to build [software that unifies users and programmers][operators].

[operators]: https://jyn.dev/operators-not-users-and-programmers/

## why ninja?

several reasons:
- ninja exposes reflection capabilities via `ninja query`, allowing flower to get a list of output files that is based on build.ninja, without having to calculate dependency graphs itself.
- ninja is concise, fully general, and not tied to a specific language ecosystem.
- separating rules (edges) from dependencies (nodes) allows extending it at runtime with custom build rules.
- the language is sharply and intentionally restricted. put your complicated trickery in your templates and meta-build code, not in the build system.

## why clojure?

i had three major design goals for the template language.
1. it needs to be easy to embed non-trival expressions inline.
this already eliminates line-oriented languages, which includes most imperative ALGOL-likes.
we're basically left with template languages and LISPs.
2. it needs to be an actual programming language.
most template languages suck! they are super restrictive for not really any good reason.
also inventing my own language is a rabbit hole i am not willing to go down.
so we are left with LISPs.
3. it needs to be fast enough that startup times are not noticeable.
i really liked the idea of implementing the SSG as *loosely coupled components*: a small core surrounded by extensive defaults.
that has two benefits: you can look at the defaults and change them,
and if you write your own code, it's very easy to upstream because the whole project is written in one language.
but in order to do that, the host code has to have very fast startup times, because the `flower` executable is invoked many times for each page.

this basically narrows it down to Clojure.
Clojure runs on the JVM, which means you can compile it to GraalVM, which means you get near-instant startup.
as a bonus, JVM langs can interop well, which means it was easy to lean on the Java ecosystem while building the project.

## why a "meta-build" system instead of something simpler?
because if i'm going to be insane enough to write my own SSG, i want it to be one that i don't rip up and throw away in a year. that means it has to be extensible *and* not break *and* be easy enough to import that i don't spend a bunch of time rewriting things away from jinja again.

# sandboxing and security
it's an SSG. it's running arbitrary code because you (or i) wrote all the code. don't treat it as a security boundary and you'll be fine.

the built-in web server is probably not resilient to any kind of malicious use. only use it for dev. use a real web server (e.g. Caddy) to serve things in prod.

the sandboxing for clojure code is "best-effort". i have not reviewed the code of the underlying SCI interpreter, and i have not done any kind of extensive testing.
i try to prevent untracked disk access, but only to prevent your dependency graph from being wrong.
