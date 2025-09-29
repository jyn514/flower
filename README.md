# flower: an SSG that's a library, not a framework

**NOTE: still in pre-alpha, blog post forthcoming**

**NOTE: flower lives on [Codeberg](https://codeberg.org/jyn514/flower) now. Github is a read-only mirror.**

`flower` is currently EXTREMELY ROUGH. this is mostly public so i can show it to people and as a tech demo.
## what is flower?
flower is a static site generator that is a library, not a framework. it comes with good defaults that allow you to get started quickly with minimum boilerplate, but scales to projects of great size and complexity without having to rewrite your code. it is extensible, pluggable, and extremely configurable—because all the code is exposed to you the creator.

flower’s guiding principles are:
1. it’s your site, you should control what’s on it.
2. power comes from structure, not expressiveness.
3. prefer composing tools to monoliths.
4. make the obvious thing the correct one.

## why should I use flower?

Unlike other static site generators, flower is implemented almost completely in "user-space".
Nearly any part of the site can be overriden.
You can have custom commands, define custom syntax highlighting, use custom markup languages or template languages, or define your own site structure that I did not anticipate.
At almost every level, I have tried to avoid restricting what is possible to do with flower: to make simple things easy, and hard things possible.

For more information, see [the flower docs site](./docs/pages/index.md).

## language overview
this is just a quick tour of the language. for more info, see [the language intro](docs/pages/language.md).

the escape character is `◊`. `◊(func args)` calls a function and emits the return value into the template. `◊x` emits the  variable `x` into the template. `◊(func args)«body»` allows nesting markup inside a function call. template embedding and includes are done with clojure function calls.
```html
◊(def body)«
 <div class="trigger">
    ◊(for [section subsections
           :when (:title section)])«
      <a class="page-link" href="◊(:path section)">◊(:title section)</a>
    »
 </div>
 »
 ◊(embed "page.html" {'body body})
```
see [syntax](./docs/pages/syntax.md) for how to type the escape characters.
## features

all the basics [^1]:

- static binaries
- live-reload
- extremely fast builds
- RSS feed support

[^1]: syntax highlighting is a [work in progress](https://codeberg.org/jyn514/flower/issues/22)

things it’s weird other SSGs don’t support:

- println debugging
- real stack traces ([example](#example-stacktrace))
- a REPL so you can try things out easily
- a real programming language (clojure). the same language is used throughout. “macros” are not different from “shortcodes” and “variables”.
- use any markup language you like. asciidoc ([TODO][todo-asciidoc]) and markdown are supported by default. other languages are pluggable.

[todo-asciidoc]: https://codeberg.org/jyn514/flower/issues/37

and some weird ones:

- support for arbitrary build commands
- import your existing site; no changes to templates or content needed to serve the same site (some amount of configuration necessary).
- post-process generated HTML based on CSS selectors. for example, create your own table of contents, or parse the `<title>` tag out of the pages headings. -- VERY WIP
- choose your own preprocessor language. you are not tied to the built-in template language; you can even use two different languages for the inline preprocessing and your templates. -- [TODO](https://codeberg.org/jyn514/flower/issues/55)
- choose your own libraries. instead of "macros" and "shortcodes", flower gives you real functions, which can be in either clojure or a language of your choosing -- [TODO](https://codeberg.org/jyn514/flower/issues/31)
- render individual files at a time. this allows you to wrap flower in an external build system and reuse its caching.

## testimonials

folks the reviews for my new ssg are in

> I hate that this is exciting to me. This makes me want to write software.  
—burned out professional programmer

> you have made a tool i might actually use.  
—girl who rolls her own crypto

## quick start

See [the quickstart docs](docs/pages/quickstart.md).

## contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## guide

This readme is very brief, because there is a lot to cover.
For a more in-depth explanation, with many examples, see [the guide](docs/pages/guide.md).

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

## flower has lots of weird ideas! where did they all come from?
- the template syntax is heavily based on [pollen] and refined through conversations with friends and real bugs i ran into while porting jyn.dev.
- transformers are based on [soupault] (but i didn't like the enormous amounts of configuration required, so i give you a real language for running transformers).
- dynamic runtime tracking of file IO was heavily based on the syscall tracking i write about in [complected and orthogonal persistence], which was inspired by many many conversations with [@edef].
- allowing people to modify so much of the site, (i.e. moving nearly everything into user space) was my idea, with lots of help from [@Hactar] on how to actually do it.
- unifying templates and pages was my own idea.
- [petal] (currently unimplemented) is based on jade-lang, maud, zen-coding, and haml.

[pollen]: https://docs.racket-lang.org/pollen
[soupault]: https://soupault.app/
[@Hactar]: https://ajfarkas.dev/
[complected and orthogonal persistence]: https://jyn.dev/complected-and-orthogonal-persistence/
[@edef]: https://github.com/sponsors/edef1c
[petal]: https://codeberg.org/jyn514/flower/issues/20

## the way you talk about flower doesn't sound like it's an SSG ....

i think of flower as **a build system masquerading as a static site generator**.
if you take away the trappings (CSS, RSS, the static file server) you are left with a general purpose applicative build system configured in clojure.
you could—and in fact, i probably will at some point—separate out the build system into a reusable library useful for other projects.

notably, this build system has some nice properties not found in other systems to my knowledge:
- like Make, flower is simple to use for simple applications. Unlike Make, flower is a serious build system.
- like CMake and Meson, the build code is clearly separated from the generated build plan. Unlike CMake, it does not have 25 years of back-compat concerns.
- like Bazel, you get a real language in which to generate the build plan. Unlike Bazel, flower is designed to be wrapped in a larger build system.
- like Tup, you get runtime dependency tracking of file accesses, including for the build code itself. Unlike Tup, dependency tracking is cross-platform.
- like Cargo, many things are defined "out of the box" and don't require extensive configuration. Unlike Cargo, you have escape hatches for complicated things.

## example stacktrace

```
flower: error: failed to eval templates/default.html
Caused by: failed to eval templates/footer.html
 [expressions.meta/embed expressions/meta.clj 21:14]
 [expressions.meta/embed expressions/meta.clj 16:1]
 [expressions.meta/include expressions/meta.clj 26:14]
 [expressions.meta/include expressions/meta.clj 24:1]
 [user/str templates/default.html 17:1]
 [clojure.core/str <host code>]

Caused by: Could not find namespace: flower.expressions.constants.
 [clojure.core/use <host code>]
 [user/<top-level> templates/footer.html 1:1]
```

# sandboxing and security
it's an SSG. it's running arbitrary code because you (or i) wrote all the code. don't treat it as a security boundary and you'll be fine.

the built-in web server is probably not resilient to any kind of malicious use. only use it for dev. use a real web server (e.g. Caddy) to serve things in prod.

automatic HTML escaping is [not currently implemented][escaping].

[escaping]: https://codeberg.org/jyn514/flower/issues/21

the sandboxing for clojure code is "best-effort". i have not reviewed the code of the underlying SCI interpreter, and i have not done any kind of extensive testing.
i try to prevent untracked disk access, but only to prevent your dependency graph from being wrong.
