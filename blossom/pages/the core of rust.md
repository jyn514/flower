---
title: the core of rust
date: 2025-08-21
extra:
  audience: PL enthusiasts
taxonomies:
  tags:
    - rust
description: "within Rust is a smaller language struggling to get out"
---

**NOTE: this is not a rust tutorial.**

> Every year it was an incredible challenge to fit teaching Rust into lectures since you basically need all the concepts right from the start to understand a lot of programs. I never knew how to order things. The flip side was that usually when you understand all the basic components in play lots of it just fits together. i.e. there's some point where the interwovenness turns from a barrier into something incredibly valuable and helpful.
> —[Jana Dönszelmann](https://donsz.nl/)

## Vision
One thing I admire in a language is a strong vision. [Uiua](https://www.uiua.org/), for example, has a very strong vision: what does it take to eliminate all local named variables from a language? [Zig](https://ziglang.org/) similarly has a strong vision: explicit, simple language features, easy to cross compile, drop-in replacement for C.

Note that you don’t have to agree with a language’s vision to note that it *has* one. I expect most people to find Uiua unpleasant to program in. That’s fine. You are not the target audience.

There’s a famous quote by Bjarne Strousup that goes “Within C++, there is a much smaller and cleaner language struggling to get out.” Within Rust, too, there is a much smaller and cleaner language struggling to get out: one with a clear vision, goals, focus. One that is coherent, because its features *cohere*. This post is about that language.

---

## Learning Rust requires learning many things at once

Rust is hard to learn. Not for lack of trying—many, many people have spent person-years on improving the diagnostics, documentation, and APIs—but because it’s complex. When people first learn the language, they are learning many different interleaving concepts:
- first class functions
- enums
- pattern matching
- generics
- traits
- references
- the borrow checker
- `Send`/`Sync`
- `Iterator`s

These concepts interlock. It is very hard to learn them one at a time because they interact with each other, and each affects the design of the others. Additionally, the standard library uses all of them heavily.

Let’s look at a Rust program that does something non-trivial:[^1]
```rust
#!/usr/bin/env -S cargo -Zscript
---
package.edition = "2024"
[dependencies]
notify = "=8.2.0"
---
use std::path::Path;
use notify::{RecursiveMode, Watcher};
fn main() -> Result<(), notify::Error> {
    let paths = ["pages", "templates", "static"];
    let mut watcher = notify::recommended_watcher(|result: Result<notify::Event, _>| {
        if let Ok(event) = result {
            let paths: Vec<String> = event.paths
                .into_iter()
                .map(|path| path.display().to_string())
                .collect();
            println!(":{:?} {}", event.kind, paths.join(" "));
        }
    })?;
    for path in paths {
        watcher.watch(Path::new(path), RecursiveMode::Recursive)?;
    }
    loop { std::thread::park(); } // sleep forever
}
```
I tried to make this program as simple as possible: I used only the simplest iterator combinators, I don't touch `std::mpsc` at all, I don't use async, and I don't do any complicated error handling.

Already, this program has many interleaving concepts. I'll ignore the module system and macros, which are mostly independent of the rest of the language. To understand this program, you need to know that:
- `recommended_watcher` and `map` take a function as an argument. In our program, that function is constructed inline as an anonymous function (closure).
- Errors are handled using something called `Result`, not with exceptions or error codes. I happened to use `fn main() -> Result` and `?`, but you would still need to understand Result even without that, because Rust does not let you access the value inside unless you check for an error condition first.
- Result takes a generic error; in our case, `notify::Error`.
- Result is an data-holding enum that can be either Ok or Err, and you can check which variant it is using pattern matching.
- Iterators can be traversed either with a `for` loop or with `into_iter()`. [^2] `for` is eager and `into_iter` is lazy. `iter` has different ownership semantics than `into_iter`.

If you want to modify this program, you need to know some additional things:
- `println` can only print things that implement the traits `Display` or `Debug`. As a result, `Path`s cannot be printed directly.
- `path.display()` returns a struct that borrows from the path. Sending it to another thread (e.g. through a channel) won't work, because `event.paths` goes out of scope when the closure passed to `recommended_watcher` finishes running. You need to convert it to an owned value or pass `event.paths` as a whole.
	- As an aside, this kind of thing encourages people to break work into "large" chunks instead of "small" chunks, which I think is often good for performance in CPU-bound programs, although as always it depends.
- `recommended_watcher` only accepts functions that are `Send + 'static`. Small changes to this program, such as passing the current path into the closure, will give a compile error related to ownership. Fixing it requires learning the `move` keyword, knowing that closures borrow their arguments by default, and the meaning of `'static`.
	- If you are using `Rc<RefCell<String>>`, which is often recommended for beginners[^3], your program will need to be rewritten from scratch (either to use Arc/Mutex or to use exterior mutability). For example, if you wanted to print changes from the main thread instead of worker threads to avoid interleaving output, you couldn't simply push to the end of an `all_changes` collection, you would have to use `Arc<Mutex<Vec<Path>>>` in order to communicate between threads.

This is a *lot* of concepts for a 20 line program. For comparison, here is an equivalent javascript program:
```javascript
const fs = require('fs');
const paths = ['pages', 'templates', 'static'];
for (let path of paths) {
  fs.watch(path, (eventType, filename) => {
    if (filename !== null) {
      console.log(`${eventType} ${filename}`)
    }
  });
}
await (new Promise(() => {})); // sleep forever
```
For this JS program, you need to understand:
- first class functions
- nullability
- yeah that's kinda it.

I'm cheating a little here because `notify` returns a list of paths and `node:fs/watch` doesn't. But only a little.

My point is not that JS is a simpler language; that's debatable. My point is that you can do things in JS without understanding the whole language. It's very hard to do non-trivial things in Rust without understanding the whole core.
## Rust's core is interwoven on purpose
The previous section makes it out to seem like I'm saying all these concepts are bad. I'm not. Rather the opposite, actually. Because these language features were designed in tandem, they interplay very nicely:
- Enums without pattern matching [are very painful to work with](https://en.cppreference.com/w/cpp/utility/variant.html) and pattern matching without enums [has very odd semantics](https://www.hillelwayne.com/post/python-abc/)
- `Result` and `Iterator`s are impossible to implement without generics (or duck-typing, which I think of as type-erased generics)
- `Send`/`Sync`, and the preconditions to `println`, are impossible to encode without traits—and this often comes up in other languages, for example printing a function in clojure shows something like `#object[clojure.core$map 0x2e7de98a "clojure.core$map@2e7de98a"]`. In Rust it gives a compile error unless you opt-in with Debug.
- `Send` / `Sync` are only possible to enforce because the borrow checker does capture analysis for closures. Java, which is *wildly* committed to thread-safety by the standards of most languages, cannot verify this at compile time and so has to [document synchronization concerns explicitly](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/text/SimpleDateFormat.html#synchronization) instead.

There are more interplays than I can easily describe in a post, and all of them are what make Rust what it is.

Rust has other excellent language features—for example the [inline assembly syntax](https://doc.rust-lang.org/nightly/reference/inline-assembly.html) is a work of art, props to [Amanieu](https://github.com/amanieu). But they are not interwoven into the standard library in the same way, and they do not affect the way people *think* about writing code in the same way.

## A smaller Rust
without.boats wrote a post in 2019 titled ["Notes on a smaller Rust"](https://without.boats/blog/notes-on-a-smaller-rust/) (and a follow-up [revisiting](https://without.boats/blog/revisiting-a-smaller-rust/) it). In a manner of speaking, that smaller Rust *is* the language I fell in love with when I first learned it in 2018. Rust is a lot bigger today, in many ways, and the smaller Rust is just a nostalgic rose-tinted memory. But I think it's worth studying as an example of how well [orthogonal](https://en.wikipedia.org/wiki/Orthogonality#Computer_science) features can compose when they're designed as one cohesive whole.

If you liked this post, consider reading [Two Beautiful Rust Programs](https://matklad.github.io/2020/07/15/two-beautiful-programs.html) by matklad.

[^1]: This program intentionally uses a file watcher because file IO is not possible to implement efficiently with async on Linux (and also because I wrote a file watcher recently for [flower](https://github.com/jyn514/flower/), so it's fresh in my mind). Tokio itself just uses a threadpool, alongside channels for notifying the future. I don’t want to get into async here; this just demonstrates Send/Sync bounds and callbacks.

[^2]: Technically, `for` is syntax sugar around `into_iter()`, but you don't need to know that for most rust programs.

[^3]: which is a terrible idea by the way, even more experienced Rust programmers often don't understand the interior mutability very well; see [this blog post by dtolnay](https://docs.rs/dtolnay/latest/dtolnay/macro._02__reference_types.html) on the difference between mutability and uniqueness in reference types. It would be better to suggest using owned types with exterior mutability and cloning frequently.
