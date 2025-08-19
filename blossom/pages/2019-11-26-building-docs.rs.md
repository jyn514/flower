---
aliases: ['/2019/11/26/building-docs.rs.html']
title: "Building Docs.rs"
date: 2019-11-26
description: How I because the 2nd-most prolific contributor to docs.rs
taxonomies:
  tags:
    - stories
    - rust
---

## What is docs.rs?

[docs.rs](https://docs.rs/) is a site dedicated to hosting documentation for Rust projects.
It automatically builds documentation for every package published to
[crates.io](https://crates.io/) using `cargo` and `rustdoc`.
Unfortunately, because `rustdoc` is tied so closely to the compiler,
this requires building every package from source using `cargo doc`.
As a result, I've heard several members of the docs.rs team describe it as
'Remote Code Execution as a Service' (since `build.rs` files can
[execute arbitrary code](https://doc.rust-lang.org/cargo/reference/build-scripts.html)).

Just from this description you can see that hosting docs.rs is an enormous job
and requires a ton of compute resources. It gets worse when you consider that
docs.rs has to _store_ all this documentation somewhere. In fact, it's in
an AWS storage bucket and gets uploaded after every build.

## How did I get involved?

My main rust project is [rcc](https://github.com/jyn514/rcc), a C compiler written in Rust.
I use [Cranelift](https://github.com/CraneStation/cranelift) to generate the assembly
code; Cranelift is a code generator like [LLVM](https://llvm.org/).
Cranelift releases very quickly - I first started using it in version
[`0.36`](https://github.com/jyn514/rcc/commit/9f5573d) and it's currently on
[`0.51`](https://docs.rs/cranelift-codegen/0.51.0/cranelift_codegen/).
Because I used the docs so much when I was getting started,
Firefox got convinced that I always wanted to visit `0.36` - see also the
[rustdoc issue](https://github.com/rust-lang/rust/issues/9461) about canonical URLs.

Fortunately, docs.rs has a little link you can click to go to the latest version.
Unfortunately, docs.rs used to redirect you to the home page whenever you clicked that link,
so if you were looking up
[`cranelift_codegen::ir::InstBuilder`](https://docs.rs/cranelift-codegen/0.42.0/cranelift_codegen/ir/trait.InstBuilder.html)
before, you'd have to click through 3 links to get back there on the latest version.
This has made a lot of people very angry and been widely regarded as a bad move.
There had actually been [an issue open](https://github.com/rust-lang/docs.rs/issues/200)
for about a year and a half at this point, although it wasn't getting much attention.

I decided since I was running into the issue so much, I may as well contribute a patch.

## What happened?

At that point, docs.rs was using [Vagrant](https://www.vagrantup.com/) to run
builds locally, because it needed to have a sandboxed environment to execute
arbitrary code on. When I tried using it, it took about 20 minutes and then I
[got an ugly error](https://github.com/rust-lang/docs.rs/issues/200#issuecomment-539771094) without any explanation.
Remember, this isn't even the hard part of the change, I just wanted to get it running from master.

I chatted with [Pietro](https://github.com/pietroalbini) on Discord
and it turns out that docs.rs had recently
[switched to a different sandbox](https://github.com/rust-lang/docs.rs/pull/407),
but the Vagrantfile never got updated. Since I don't know really how to use Vagrant
or Ruby (and no one was volunteering to fix it), I didn't have any hope of getting
that working. I asked what I could do and someone pointed me to
[the wiki](https://github.com/rust-lang/docs.rs/wiki/Self-hosting-outside-the-Vagrant-VM).
I took one look and knew there was no way I wanted to do this - it's about 5 pages,
and 30 manually steps, most of which have to be done in order.
So I figured, hey, I'm a developer, let's automate this!

Long story short, I ended up [writing a `Dockerfile`](https://github.com/rust-lang/docs.rs/pull/432)
and Pietro was so impressed that he started [using it in production](https://github.com/rust-lang/docs.rs/pull/455) :D

After a month and what has been called
> [Unprecedented (but greatly appreciated) levels of yak shaving](https://users.rust-lang.org/t/rust-2020-growth/34956/43)

I finally [got the original change merged](https://github.com/rust-lang/docs.rs/pull/454) :)

## What next?

Pietro and [QuietMisdreavus](https://github.com/QuietMisdreavus) were so impressed
with my work that they invited me to the team! As of November 1st, I have push access
to `docs.rs` :) (Pietro still handles the deploys)

I've been helping with [a](https://github.com/rust-lang/docs.rs/pull/487)
[few](https://github.com/rust-lang/docs.rs/pull/485)
[other](https://github.com/rust-lang/docs.rs/pull/476)
[PRs](https://github.com/rust-lang/docs.rs/pull/468)
[since](https://github.com/rust-lang/docs.rs/pull/460)
and plan to contribute for the foreseeable future!

## Acknowledgments

Thank you so much to Pietro and QuietMisdreavus for helping me get started with
the project! My favorite part of working on docs.rs is giving and receiving feedback
from my friends.

Thank you to [/u/17cupsofcoffee](https://users.rust-lang.org/u/17cupsofcoffee)
for prompting me to write this blog post!

And finally, thanks to everyone in the Rust community for making this an awesome
language to work on :D
