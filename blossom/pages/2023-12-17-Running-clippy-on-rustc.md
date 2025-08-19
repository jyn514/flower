---
title: "Running clippy on rustc"
date: 2023-12-17
description: How I got clippy to lint on the code for the compiler itself
draft: true
taxonomies:
  tags:
    - rust
---

Clippy is an official tool for linting your code. It's named after the venerable *Clippit Office
Assistant* from Windows XP. It has like a bajillion lints. It works really well.

Unfortunately, the rust compiler has a [weird bespoke build system](./2023-01-12-Bootstrapping-Rust-in-2023.md),
so adding things to it is rather difficult. As a result, I have been trying to add a reliable `x clippy` command to the build system for going on *checks notes* [three and a half years now][#77351].

[#77351]: https://github.com/rust-lang/rust/pull/77351.

This blog post is about what makes it hard, how I fixed it, and why it took so long.

---

Say you don't know anything about compilers or bootstrapping. How might you approach this problem?
Well, a good first try is probably just to run `cargo clippy`. It works everywhere else, after all. How bad could it be?

```
error: expected identifier, found `$`
   --> library/core/src/tuple.rs:172:26
    |
172 |                     $( ${ignore($T)} self.${index()} == other.${index()} )&&+
    |                          ^^^^^^ - help: try removing `$`

... snip ...

error[E0277]: the trait bound `(FromA, FromB): default::Default` is not satisfied
    --> library/core/src/iter/traits/iterator.rs:3442:44
     |
3442 |         let mut unzipped: (FromA, FromB) = Default::default();
     |                                            ^^^^^^^^^^^^^^^^ the trait `default::Default` is not implemented for `(FromA, FromB)`
     |
     = help: the trait `default::Default` is implemented for `()`
error: could not compile `core` (lib) due to 16 previous errors; 716 warnings emitted
```

Well. That wasn't particularly helpful now, was it. The problem, as explained in more detail in
[the rustc dev guide], is that standard library uses unstable features that may not match the
features available in our default Rust toolchain. As a result, it only supports exactly two versions
of the compiler: the previous beta, and the latest commit on the master branch.

[the rustc dev guide]: https://rustc-dev-guide.rust-lang.org/building/bootstrapping.html

Now, we can't use the previous beta directly because that's gated behind a `#[cfg(bootstrap)]` attribute,
but we *can* jerry-rig that into the build system.

```
```
