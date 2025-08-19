---
aliases: ['/2023/01/12/Bootstrapping-Rust-in-2023.html']
title: "Why is Rust's build system uniquely hard to use?"
date: 2023-01-12
description: Goals for improving Rust's build system and making it easier to understand
taxonomies:
  tags:
    - ideas
    - rust
---

*This post will assume you have watched <https://www.youtube.com/watch?v=oUIjG-y4zaA>.
You may also find it helpful to read <https://rustc-dev-guide.rust-lang.org/building/bootstrapping/what-bootstrapping-does.html#stages-of-bootstrapping>, but I won't assume prior knowledge of the information there.*

<!-- This is a post about how I would like Rust's bootstrapping system to work in an ideal world. -->
<!-- I am not sure all these changes are feasible - they certainly can be done at a technical -->
<!-- blah blah coordination is hard -->

## Why is bootstrap confusing?

People get confused by Rust's build system a lot. I have been trying for a while to figure out what
makes Rust uniquely hard here, as a lot of the people who are confused are experienced compiler
engineers who have used staged compilers in the past. Here are some theories I have.

### Is "stage 0" is uniquely confusing?

#### What do other compilers call "stage 0"?

<https://gcc.gnu.org/install/build.html> refers to a "3-stage" build, and names the stages "stage1", "stage2", "stage3".
It also references a "native compiler". As far as I can tell, "native compiler" corresponds to what Rust calls "stage 0", and stage 1/2/3 are all exactly equivalent, i.e:
- stage0 is a pre-existing compiler, which is assumed to already exist (or in rust's case, a downloaded beta compiler).
- stage1 is the sources from latest master, built by stage0, and has a different ABI from stage2 and stage3.
- stage2 is the same sources, built by stage1.
- stage3 is byte-for-byte identical with stage2, only useful for verifying reproducible builds.

<https://llvm.org/docs/AdvancedBuilds.html#bootstrap-builds> says "In a simple two-stage bootstrap build, we build clang using the system compiler, then use that just-built clang to build clang again." which again seems to match GCC and Rust.

<https://gitlab.haskell.org/ghc/ghc/blob/master/hadrian/README.md#staged-compilation> seems to match GCC, Clang, and Rust.

<!-- TOOD: mention that there are other self-hosted compilers that don't use stages? would make it clear that having "too close" a model to other compilers is confusing -->

#### Why would "stage 0" be confusing?

Maybe treating this as "just" another stage, rather than naming it "native compiler" or "system compiler", is confusing.
That alone seems unlikely though; "0" at least to me seems like a good indication that it's not being built from source.
So renaming stage 0 to "bootstrap compiler" or "pre-compiled compiler", while helpful, seems unlikely to clear up the confusion.

### Is building the standard library confusing?

#### What do other compilers do?

GCC and Clang do not build their standard libraries from source. Instead, they use the same dynamically linked system standard library for all stages, including the "stage 0" or "system compiler" stage.

(As an aside, that's *horrifying*, C doesn't have a standardized ABI and so this can cause miscompilations even if there are no bugs in the standard library or gcc itself: <https://faultlore.com/blah/c-isnt-a-language/#c-doesnt-actually-have-an-abi>)

GHC *does* build its standard library from source. It has two parts to its standard library:
1. `GHC.Base`, which can only be compiled by exactly one version of GHC. This is a runtime, analogous to `crt0.o` or [`std::rt`](https://stdrs.dev/nightly/x86_64-unknown-linux-gnu/std/rt/index.html).
2. Everything else in the `GHC.*` namespace. This can be compiled by any version of GHC.

#### What does Rust do?

Rust also builds its standard library from source. It has three parts:
1. `core`
2. `alloc`
3. `std`

All three can be built by exactly *two* versions of `rustc`:
1. The previous beta compiler.
2. The in-tree sources, versioned in the same git repo.

As discussed in my RustConf talk, they distinguish the two with `cfg(bootstrap)`; when set, we're using beta, when unset, we're using the in-tree sources.

#### Why would this be confusing?

Having to support two versions of the compiler seems to be unique to Rust's standard library. C bypasses the question altogether by not having language intrinsics in the standard library and supporting any compiler version; Haskell only requires one version of the compiler to be supported.

Having to support two versions is in fact the original motivation for this post, since it causes
lots of pain for changes that modify both the compiler and standard library; see
<https://github.com/rust-lang/rust/pull/84863> for an example that modifies both in the same PR and
<https://github.com/rust-lang/rust/pull/99917> for an example that depends on changes to the
compiler that haven't yet landed on beta.

### What can we do about it?

Supporting two versions is not an intrinsic requirement. We do it for two reasons:
1. It allows testing changes to the standard library without having to first build the compiler.
   [Building the compiler is painfully slow](https://github.com/rust-lang/rust/issues/65031).
2. It allows [using nightly standard library features](https://rustc-dev-guide.rust-lang.org/building/bootstrapping/what-bootstrapping-does.html#complications-of-bootstrapping) in the compiler before they land on beta.

1 is "just" implementation work to fix: if there are no changes to the compiler, we can download CI artifacts for that commit and use those instead. There are [a few bugs to fix](https://github.com/rust-lang/rust/issues/81930) but they're surmountable.

2 is harder. Either we add `cfg(bootstrap)` to the compiler to use a different implementation when building with stage0 than stage1, or we stop using nightly standard library features until they reach beta. See <https://rust-lang.zulipchat.com/#narrow/stream/131828-t-compiler/topic/Building.20rustc.20with.20beta.20libstd/near/209899890> for a very (very) long discussion of the tradeoffs here.

There are some more benefits to supporting a single version not discussed there:
- Rebasing over master recompiles much less code. Modifying a single line in `core` no longer requires rebuilding the world; only changes to the compiler require the compiler to be rebuilt.
- Modifying the standard library locally don't require rebuilding the compiler. This is especially relevant to people who are changing how the standard library interacts with the compiler; we would be able to remove `--keep-stage-std 0` and all associated footguns as a workflow altogether.
- Creating a new Rust release requires touching *drastically* less `cfg(bootstrap)` since lang items no longer need to be modified, only small parts of the compiler.

I've put together some data on how often using those features before they hit beta happens in
practice, and - at least from 1.61.0 onwards - it appears it *never* happens. See
<https://github.com/jyn514/rust/tree/versions-used> for how that data was gathered (run
`./collect_new_versions.sh`). What's more common is renaming a method before it's stable; see
<https://github.com/rust-lang/rust/pull/79805/files> for an example. The `cfg(bootstrap)` code to
handle this in the compiler should be pretty simple.

I've talked to people on both T-libs and T-compiler and they say that removing `cfg(bootstrap)` would be an *enormous* help. Some testimonials:

> [@thomcc][thom]: Yes please. It's a huge headache. It also frequently comes up as a reason not to let the const-eval team experiment with stuff, since we know `~const` is likely going away and don't want to deal with the "lol we `cfg(bootstrap)`ed off all of core::iter".

[thom]: https://github.com/thomcc

> [@fee1-dead][fee1-dead]: Working on const traits makes the bootstrap issue very apparent because almost all bugs would be found from attempting to use the feature in the standard library. Fixes for those bugs would need to wait six weeks before finally released as the beta compiler, which slows down development and evolution of the feature.

[fee1-dead]: https://github.com/fee1-dead

> [@m-ou-se][mara]: Sometimes it gets super messy with all the `cfg(bootstrap)` stuff for things relying on built-in macros or new lang items. Please fix cfg bootstrap hell :D

[mara]: https://github.com/m-ou-se

> [@workingjubilee][workingjubilee]: Using beta stdlib would make it much easier to experiment outside the compiler/stdlib proper.

[workingjubilee]: https://github.com/workingjubilee

> [@Nilstrieb][Nilstrieb]: I'd rather have a few cfgs in the compiler when necessary instead of cfgs in std all the time.

[Nilstrieb]: https://github.com/nilstrieb

#### "But that just moves the cfg(bootstrap) to the compiler!"

A common (incorrect) objection is that, after this change, adding new language items would require adding `cfg(bootstrap)` to the compiler.
This is false. The compiler *also* only has to support building one version of `std` after this change. The only time the bootstrap standard library is involved is when *building the compiler*. Unlike how `std` is intrinsically tied to the compiler due to lang items, the compiler doesn't intrinsically depend on implementation details of the standard library; it only uses them for dogfooding.

(I don't want to hear about how `lang` items are ideologically impure. I don't care. It's not changing.)

Here is a graph of what the build would like before:

| component | built-by | building |
| --- | --- | --- |
| compiler | 1 std | 1 std |
| std | 2 compilers | NA |

and after:

| component | built-by | building |
| --- | --- | --- |
| compiler | 2 std | 1 std |
| std | 1 compiler | NA |


### Is the "stage" terminology itself confusing?

#### What do other compilers do?

For GHC, `build stage1:exe:ghc-bin` builds stage1 GHC with the stage0 compiler.
 <!-- https://discourse.llvm.org/t/how-to-run-individual-phases-of-a-2-or-3-stage-build/2596/2 -->
For Clang, `ninja stage2` builds the stage2 clang with the stage1 compiler and `ninja clang-bootstrap-deps` builds the stage1 clang with the stage0 compiler.
<!-- https://github.com/ocaml/ocaml/blob/64ef2d0ce1eb7d5f09ac6cde1a78f74b62804cc6/Makefile#L698-L702 -->
<!-- https://github.com/ocaml/ocaml/blob/64ef2d0ce1eb7d5f09ac6cde1a78f74b62804cc6/Makefile#L833-L841 -->
OCaml uses `make coreall` to build the stage1 OCaml with the bootstrap compiler and `make bootstrap` to build a full bootstrap compiler.
<!-- zig is a pain in the ass to build on windows because it needs llvm installed -->
<!-- pypy doesn't have stages -->
<!-- swift isn't self-hosted https://github.com/apple/swift/blob/2c7b0b22831159396fe0e98e5944e64a483c356e/www/FAQ.rst -->
<!-- http://www.sbcl.org/porting.html is unclear and I don't feel like building it -->

#### What does Rust do?

`x build --stage 0 rustc` builds stage1 rustc with the stage0 compiler.
`x build --stage 1 rustc` builds stage2 rustc with the stage1 compiler.

This is off-by-one from how *every other modern compiler* counts stages.

<https://rustc-dev-guide.rust-lang.org/building/bootstrapping/what-bootstrapping-does.html#understanding-stages-of-bootstrap>
spends several paragraphs talking about how `build --stage N` means "build with stage N", not "create the
compiler that lives in the stage N sysroot".  All the people I've talked to who say this
meaning of `--stage N` is intuitive have been using `x.py` for several years and are experts in the
system. Nearly all the people I've talked to who find it confusing are either new to the compiler,
or contribute regularly but aren't experts in Rust's build system - even those who are experienced
in bootstrapping compilers for other languages!

In the words of [@Nilstrieb][Nilstrieb]:

> You build a *target*. The focus is always *what* you build.

<!-- TODO: talk with manish about how to make this less combative -->
We are not meeting that intuition today with x.py.  It seems unfortunate to have a system that only
makes sense if you've used it for a long time and are accustomed to it. If you're using it for
several years, you have time to relearn the system.

#### What can we do instead?

I would like to introduce four new flags:
- `--bootstrap-sysroot`
- `--dev-sysroot`
- `--dist-sysroot`
- `--reproducible-sysroot`

This correspond closely, but not exactly, to `--stage 0/1/2/3` (respectively).
Here is a conversion guide between the two: ![conversion guide (see /assets/bootstrapping.tex for source)](/assets/bootstrap-sysroot-conversion.png)

I propose *not* putting this in the dev-guide, but creating an inside-rust post which we link to in bootstrap's changelog.
The idea is for people who've already been using x.py to see the guide, but not people learning the tool for the first time.
We would keep `--stage` for a time, but eventually deprecate it.

I want to call out a few interesting properties of these flags:
- `build --bootstrap-sysroot std` makes it more clear how *strange* it is that `std` is built before rustc. This isn't something people will need to think about if we change `std` to only need to support one compiler.
- The names are self-describing. People don't have to wonder whether `--stage 1` is the flag they want or not; `--dev-sysroot` makes it clear it is.
- The only times `--bootstrap-sysroot` will be used is for
    - `doc` / `clippy` (to use the beta tools instead of recompiling, although `--dev-sysroot` will still be supported). Given that `download-rustc` will be a blessed workflow, we may want to drop support for this in the future.
    - `build expand-yaml-anchors` (or other bootstrap tools); *not* the standard library or compiler.
- `check` will only ever support one stage (`--dev-sysroot` for the compiler and std; `--bootstrap-sysroot` for bootstrap tools)
- `test --dev-sysroot ui` now matches the sysroot of `build compiler` (!). I am planning to make `test rustc_data_structures --dev-sysroot` compile `rustc_data_strucutres` (like `--stage 0` today) for consistency, and so that we are always testing the compiler *that will end up* in the given sysroot.
- `build --dev-sysroot rustdoc` now matches the sysroot of `build --dev-sysroot compiler` (!). Hopefully we can also do this for clippy and miri.

To make the new flags easier to learn, we can name the sysroots directories after the flags: `build/host/{bootstrap,dev,dist}-sysroot`.
I have not yet decided if we should introduce a `reproducible-sysroot` or not; see <https://github.com/rust-lang/rust/issues/90244#issuecomment-1120649548> for some of the difficulties involved.

## "Misc breaking changes"

Skimmed over in the previous sections is how to get people to use `download-rustc`.
If we enable it unconditionally for everyone, distros will get very upset.
If we don't enable it, people working on the standard library will have horrifically long compile times.

To avoid this, we could *require* people to set a profile for `config.toml`. To avoid making distros
hate us too much, `./configure` would set the `user` profile; running `./x.py build` without
creating a profile would give a hard error pointing you to `x.py setup`. We would still treat an
empty config.toml as opting-in to no profile.

## Summary of future work

Here are all the proposed changes in this post, gathered in one place:

- Change `./configure` to set `profile = "user"`.
- Make `profile` required in config.toml.
- Fix the existing bugs in `download-rustc`.
- Enable `download-rustc = "if-unchanged"` by default for the `library` profile.
- Get rid of `build --stage 0 std`. The compiler will be unconditionally built with beta std, not nightly std.
    - Get rid of `stage0-sysroot`.
    - Make sure `download-rustc` doesn't build the compiler from source if there are only library changes; this needs to be careful to still rebuild stage 2 rustc if there are library changes.
- Rename `build/host/stage{0,1,2}` to `build/host/{bootstrap,dev,dist}-sysroot`.
- Add `--{bootstrap,dev,dist}-sysroot` flags.
    - When doing this, clippy and miri will start using the same flags as rustdoc. See <https://rust-lang.zulipchat.com/#narrow/stream/326414-t-infra.2Fbootstrap/topic/Stage.20numbering.20for.20tools/near/298189698> for how tool flags are numbered today; after this change, both `build --dev-sysroot rustdoc` and `build --dev-sysroot clippy` will build rustc once, as a library.

That's a lot of breaking changes and a lot of work, for things we are not sure will make the user
experience easier.  To avoid multiple breaking changes in short succession, I propose making all the
changes at once, and inviting people to try out the changes from a branch before merging them. If we
have trouble getting user feedback, I could create a standalone binary which uses the new
`*-sysroot` flags even on the master branch. Note that this will not be possible for the changes
removing `cfg(bootstrap)` from std, since that requires changes outside of bootstrap.

## Questions? Concerns? Hate mail?

Feel free to contact me (`@jyn`) in <https://rust-lang.zulipchat.com/#narrow/stream/326414-t-infra.2Fbootstrap>.
