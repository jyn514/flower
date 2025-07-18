# flower: a meta-SSG based on [pollen] and [hakyll]

[pollen]: https://docs.racket-lang.org/pollen
[hakyll]: https://jaspervdj.be/hakyll/

## features

all the basics:

- static binaries
- syntax highlighting
- live-reload
- RSS feed support

and some weird ones:

- support for arbitrary build commands
- import your existing site; no changes to templates or content needed to serve the same site (some amount of configuration necessary)
- default templating language is a fully-featured programming environment (clojure). the same language is used in posts and templates, with no restrictions.
- choose your own language. you are not tied to the built-in template language; you can even use two different languages for the inline preprocessing and your templates.

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

## how do i use it?

the smallest flower site is simply a markdown file with your content:
```
$ cat src/look-ma-new-SSG.md
# look ma, new SSG!

i built a new SSG, and it works!
```
that generates a very basic scaffold with an index of posts and your rendered writing.

you probably want to customize your site, though. a simple flower site could look like this:

```
$ tree src/ lib/
src
└── look-ma-new-site.md
lib
├── macros.clj
├── index.html.clj
└── page.html.clj
```

## kinds of files
flower has three kinds of files:
- pages
- templates
- preprocessed files

let's look at them one at a time.

### pages

"look-ma-new-site.md" is a "page", like most other SSGs. it can contain metadata as frontmatter:
```
---
title: Look ma, new site!
description: how i built a new site using flower
---

flower is cool because it lets me choose my own syntax highlighter!
```

### preprocessing pages
preprocessors are hot-swappable. you can use any you like, such as handlebars, Hugo, or even the C preprocessor.

by default, flower enables a clojure preprocessor, which looks like this (credit [Scott Aaronson](https://scottaaronson.blog/?p=8088)):
```clojure
◊(def bb5 "47,176,870")

# BusyBeaver(5) is now known to be ◊(bb5)

Only now, after an additional 41 years, do we know the fifth Busy Beaver value.
Today, an international collaboration called bbchallenge is announcing that
it’s determined, and even formally verified using the Coq proof system, that
BB(5) is equal to ◊(bb5)—the value that’s been conjectured since 1990, when
Heiner Marxen and Jürgen Buntrock discovered a 5-state Turing machine that runs
for exactly ◊(bb5) steps before halting, when started on a blank tape. The new
bbchallenge achievement is to prove that all 5-state Turing machines that run
for more steps than ◊(bb5) actually run forever—or in other words, that ◊(bb5)
is the maximum finite number of steps for which any 5-state Turing machine can
run. That’s what it means for BB(5) to equal ◊(bb5).
```

that replaces all instances of `◊(bb5)` with the string `47,176,870`.
see [Pollen: The lozenge](https://docs.racket-lang.org/pollen/pollen-command-syntax.html#%28part._the-lozenge%29) for how to type the escape character and background about how it was picked.
flower embeds a fully-featured clojure interpreter; see [Learn Clojure](https://clojure.org/guides/learn/clojure) for more information.

as a shortcut, `◊ident` can be used in place of `◊(ident)`, as long as `ident` is a single clojure identifier.

you may want to write your own libraries for your posts (these are often called "shortcodes" or "macros" in other SSGs). to do so, you write normal clojure. here's an example function that transforms `◊(kbd ctrl+k f)` into `<kbd>ctrl + k</kbd><kbd>f</kbd>`:
```clojure
◊(def sed clojure.string/replace)
◊(defn kbd [keys]
  (str "<kbd>"
    (sed (sed keys " " "</kbd><kbd>")
      "+" " + ") "</kbd>"))

my tmux prefix key is ◊(kbd "ctrl+k f").
```
flower also embeds the `hiccup` library, with `html` in the default namespace, so we could have written that in a more functional style:
```clojure
◊(defn kbd [keys]
  (str/join ""
    (map #(html [:kbd (sed % "+" " + ")])
         (str/split keys #" "))))
```
if you want to reuse this code between posts, place it in `lib/kbd.clj`. the directory and file extension are important, but the file name isn't; all .clj files in `lib/` will be loaded, unless it's a template or preprocessed file as discussed below.

## templates
above, we had `title` and `description` metadata keys in our page. that metadata is *not* interpreted by flower itself, but by your templates. templates are any file in `lib/` ending with `.html.clj`.

here's a simple example of what `lib/page.html.clj` could look like:
```
<!DOCTYPE html><html>
<head><title>the website of jyn</title></head>
<body>◊(content)</body>
</html>
```
note that this is exactly the same syntax as before, we just have a `content` local variable available to us now. see the API reference for a full list of locals injected by flower.

if you want to change which template is used for a single file, add `template = my-template.html.clj` to the frontmatter, with the filename relative to the `lib` dir.

## preprocessed files
so far we have been working with pages and templates.
but these are not the only options available to us.
say you have a 404 page that looks very different than the rest of your site.
you could create a `lib/404.html.clj` and a corresponding `src/404.md`.
but that's a little silly, right? if would be nice if you could just generate the file directly.

with flower, you can. if you name your file `src/404.md.clj`, it will automatically be preprocessed, but without injecting a template before or after the content.
then, it will be rendered as markdown to HTML.

if you just want to write raw HTML, no problem. just name your file `src/404.html.clj` instead.

## changing preprocessors

say you want to work with handlebars instead of clojure.
to do so, change your page metadata to name a template that ends with `.hbs` and to name the handlebars preprocessor:
```
template = "post.hbs"
preprocessors = ["handlebars"]
```
why two separate options? in case you want to have a template generated with clojure, but a post generated with handlebars, or the other way around.

### advanced: custom preprocessors

TODO

## custom build tasks
say you are building a demo of a Rust program that compiles to WASM and runs in the browser. you want to integrate that build with the build of your site.

rather than running many commands in sequence (and losing live-reload as a result), you can tell flower to build the program for you.
you do so by writing a [`lib/rules.ninja` file](https://ninja-build.org/manual.html#_writing_your_own_ninja_files).
here's an example file that could build that rust program for us:
```ninja
rule wasm
  command = cd my-project && cargo build --release --target wasm32-wasip1

# incomplete dependency list; use https://github.com/declantsien/cargo-ninja for something more accurate
build my-project/target/release/my-bin: wasm my-project/src/main.rs my-project/Cargo.toml

build public/static/my-bin: link my-project/target/release/my-bin
```
`link` is a pre-defined rule in flower's standard library. all other commands are documented in the ninja manual.

### advanced: generating `rules.ninja` with a custom command

TODO

## importing existing sites
we now know enough to see how to import an existing static site with an existing build process.
the basic idea is to write a custom build task that "mounts" the existing site at a specific URL.
you can mount anywhere (including the site root!) as long as you do not conflict with any other files generated by flower.

this process is common enough that there is a custom wrapper for it.
the wrapper will automatically check for conflicts and error if it detects any.
otherwise, it will generate the `rules.ninja` file for you, with dependency tracking.
to use it, put your code in `lib/rules.py` instead of `lib/rules.ninja`:
```python
def rules(writer):
  writer.custom(command="cd my-zola-site && zola build")
  writer.mount(directory="my-zola-site/public", where="/zola")
```

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
### ok but really why clojure
clojure runs on the JVM, which means:
- you get any dependencies you want,
- most importantly, you get GraalVM, which gives you native binaries and extremely fast startup times. no need to install the JRE or the racket compiler; the bundled executable comes with everything you need.
## why a "meta-build" system instead of something simpler?
because if i'm going to be insane enough to write my own SSG, i want it to be one that i don't rip up and throw away in a year. that means it has to be extensible *and* not break *and* be easy enough to import that i don't spend a bunch of time rewriting things away from jinja again.
