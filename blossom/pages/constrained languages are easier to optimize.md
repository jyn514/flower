---
title: constrained languages are easier to optimize
date: 2025-07-12
aliases: ['/high-level-languages-are-easier-to-optimize/']
description: exposing raw pointers make the optimizer’s job horribly hard. high level languages can constrain your program, making more optimizations sound.
taxonomies:
  tags:
    - compilers
---
## jyn, what the fuck are you talking about
a recurring problem in modern “low-level” languages[^2] is that they are hard to optimize. they [do not reflect the hardware](https://queue.acm.org/detail.cfm?id=3212479), they require doing [complex alias analysis](https://www.ralfj.de/blog/2018/07/24/pointers-and-bytes.html), and they [constantly allocate and deallocate memory](https://medium.com/@jbyj/my-javascript-is-faster-than-your-rust-5f98fe5db1bf). [^1] they looked at [the structure/expressiveness tradeoff](https://buttondown.com/hillelwayne/archive/the-capability-tractability-tradeoff/) and consistently chose expressiveness.
## what does a faster language look like
consider this paper on [stream fusion in Haskell](https://www.cs.tufts.edu/~nr/cs257/archive/duncan-coutts/stream-fusion.pdf). this takes a series of nested loops, each of which logically allocate an array equal in size to the input, and optimizes them down to constant space using unboxed integers. doing the same with C is inherently less general because the optimizing compiler must first prove that none of the pointers involved alias each other. in fact, optimizations are so much easier to get right in Haskell that [GHC exposes a mechanism for users to define them](https://wiki.haskell.org/GHC/Using_rules)! these optimizations are possible because of [referential transparency]—the compiler statically knows whether an expression can have a side effect.

[referential transparency]: https://softwareengineering.stackexchange.com/questions/254304/what-is-referential-transparency

“haskell is known for performance problems, why are you using it as an example. also all GC languages constantly box and unbox values, you need raw pointers to avoid that.”

GC languages do constantly box and unbox [^4], but you don’t need raw pointers to avoid that. consider [futhark](https://futhark-lang.org), a functional parallel language that compiles to the GPU. its benchmarks show it being [up to orders of magnitude faster than sequential C](https://futhark-lang.org/performance.html) on problems that fit well into its domain. it does so by having unboxed fixed-size integers, disallowing ragged arrays, and constraining many common operations on arrays to only work if the arrays are statically known to have the same size.

futhark is highly restrictive. consider instead SQL. SQL is a declarative language, which means the actual execution is determined by a query planner, it’s not constrained by the source code. SQL has also been around for decades, which means we can compare the performance of the same code over decades. it turns out [common operations in postgres are twice as fast as they were a decade ago](https://rmarcus.info/blog/2024/04/12/pg-over-time.html). you can imagine writing SQL inline—wait no it turns out C# [already has that covered](https://learn.microsoft.com/en-us/dotnet/csharp/linq/).

SQL is not a general purpose language. but you don’t need it to be! your performance issues are not evenly distributed across your code; you can identify the hotspots and choose against a language with raw pointers in favor of one more structured and therefore more amenable to optimization.
## sometimes you need raw pointers
there are various kinds of memory optimizations that are only possible if you have access to raw pointers; for example [NaN boxing](https://piotrduperas.com/posts/nan-boxing), [XOR linked lists](https://github.com/laurelmay/xorlist), and [tagged pointers](https://en.wikipedia.org/wiki/Tagged_pointer?wprov=sfti1). sometimes you need them, which means you need a language that allows them. but these kinds of data structures are very rare! we should steer towards a general purpose language that does not expose raw pointers, and only drop down when we actually need to use them.
## what does a faster general purpose language look like
well, Rust is a good step in the right direction: raw pointers are opt-in with `unsafe`; Iterators support functional paradigms that allow removing bounds checks and [fusing stream-like operations](https://ntietz.com/blog/rusts-iterators-optimize-footgun/); and libraries like [rayon](https://docs.rs/rayon/latest/rayon/) make it much easier to do multi-threaded compilation.

but i think this is in some sense the wrong question. we should not be asking “what language can i use everywhere for every purpose”; we should build meta-languages that allow you to easily use the right tool for the job. this is already true for regular expressions and query languages; let’s go further. i want inline futhark; inline CSS selectors; inline datalog; ffi between python and C that’s trivially easy. the easier we make it to interop, the easier it becomes to pick the right tool for the job. <!-- TODO: link to systems thinking post -->  <!-- TODO: link to composable compilers -->

next time you hit a missed optimization, ask yourself: why was this hard for the compiler? can i imagine a language where optimizing this is easier?

## what have we learned?
- languages that expose raw pointers are surprisingly hard to optimize
- by constraining the language to require additional structure, the compiler has much more freedom to optimize
- by making it easier to switch between languages, we make it easier to choose the right tool for the job, increasing the performance of our code

[^1]: also, they require doing PGO ahead of time instead of collecting info dynamically at runtime. but i haven’t found any benchmarks showing Java/luaJIT programs that are faster than equivalent C, so i won’t claim that JIT is inherently faster.

[^2]: this is true for all of C, C++, and unsafe Rust (and to some extent Fortran, but Fortran [does not require alias analysis](https://beza1e1.tuxen.de/articles/faster_than_C.html)).

[^3]: in fact such a meta-language already exists, it’s called XML. XML is not a good language for programming in though.

[^4]: true in the general case, but not always in practice. in Go and Java, the compiler needs to do escape analysis to know whether a variable can be unboxed. in Haskell, the situation is more complicated because of lazy evaluation; see [Alexis King on the GHC strictness analyzer](https://youtu.be/XiTO1EGKrhE?si=z1IqPD4KdEkqcYd9) for more info.
