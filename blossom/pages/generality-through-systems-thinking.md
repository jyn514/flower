---
title: "generality through systems thinking"
date: 2025-07-04
description: "our programs have been stuck in a box for a long time. we can escape it."
taxonomies:
 tags: ["ideas"]
 computer-of-the-future: ["2"]
extra:
  category: principles
  stub: true
  draft: true
  toc: true

#  audience: "everyone"
#  unlisted: true
---
> And always, he fought the temptation to choose a clear, safe course, warning 'That path leads ever down into stagnation—Frank Herbert

in the previous post, I talked about how data is trapped inside the box that is our programs. In this post, I want to explore what we can do about it.
## creating coordination mechanisms
to introduce structure to system. The first is to build coordination mechanism to establish standards so that programs can inter operate along the API boundary this works, and it works quite long! But it requires changes from every program that improves the standard. Usually, standards like this only say gradual adoption over a period of many years, and once it’s widely adopted, the protocol is very resistant to see this for example in XMPP and email, both of which are effectively frozen in the early 90s.
## reusing coordination mechanisms
The second is to work with the boundaries that already exist. for example, you can track and interpose behavior along system calls, function calls, network calls, and disk IO; using ptrace, LD_PRELOAD, network proxies, and FUSE respectively. what do these have in common? they have an existing defined interface boundary, where programs 
expect only a *behavior*, not an *implementation*.

here are some example tools which work in this way:
## escaping the box
the approach i take in this series is to instead use runtime tracking at the lowest interfaces between the program and the outside world: syscalls, cpu instructions, and [ELF files][] [^2]; interactions that cannot possibly be faked and are required for all programs that run anywhere on the system. this loses portability between OS’s and static analysis. but in turn it gains **generality**: we do not need to establish a new coordination mechanism between any two processes, and our system does not need to special-case any program, because we use the same approach for all of them.

by doing so, we “escape the box”. by moving features outside the process, switching costs are greatly reduced: if we build things at the OS level, we don't have to rewrite them for each program, so the interface boundary is smaller. our systems work even for languages that have not yet been invented! in some sense, this series is an exploration of just how good we can make our tooling without first establishing a new coordination mechanism.

note that this isn’t “just another tool” because programs running in this system can interact freely with programs outside it. there is no kind of vendor lock in. i call this [**systems thinking**](https://en.m.wikipedia.org/wiki/Systems_thinking#Characteristics) because it works at the boundaries of the systems that already exist, in full detail, rather than at the level of the abstractions that are normally built on top. systems thinking is not limited to Unix processes; you can apply it to (e.g.) distributed systems, performance tracking, and debugging.
## does this actually work?
this systems-level approach is surprisingly powerful! here are some existing tools that work at this level:
- docker. this works by sandboxing all processes and interposing an [overlay filesystem] to track their file writes. this sandbox uses linux-specific mechanisms, which is why docker runs in a linux VM on macOS and Windows.
- SystemD [socket activation](https://0pointer.de/blog/projects/socket-activation.html), which decouples the socket file descriptor from the program listening to it, allowing services to be "lazy-activated" when the other side of the socket is written to
- syscall tracking using [strace]
- stack backtraces using [DWARF].
- debuggers (gdb, lldb, etc). these encode quite a lot of information about the language itself, but in theory work for any language with a C-compatible callstack.
- time-travel debuggers, like [rr]. these work by recording and replaying syscalls, so they can work no matter how many layers of FFI are going on in the program.
- dynamically loaded library metadata using [ldd] (and in general the dynamic loader has many surprising features most people don’t know about).

[DWARF]: https://dwarfstd.org
[ldd]: https://man7.org/linux/man-pages/man1/ldd.1.html
[strace]: https://strace.io
[rr]: https://rr-project.org/
[overlay filesystem]: https://docs.kernel.org/filesystems/overlayfs.html
## does this only work for “C-like” languages?
note that all of the above debugging tools are hamstrung by languages with an embedded interpreter; they show information that is accurate but contains far too much info about the runtime internals to be useful to a programmer in that language. in response, people build language specific tools such as [PDB](https://docs.python.org/3/library/pdb.html) and [Delve](https://github.com/go-delve/delve).

this limitation is specific to *mapping runtime info back to the source language*. if you do not attempt to map back to the source language—for examples, schemes 1 and 2 in [completed and orthogonal persistence](/complected-and-orthogonal-persistence/#but-why)—you do not need language specific tooling, and you can get systems that work in full generality for any language. for instance rr can *replay* any process even though it cannot let you debug a python process at the level you want to see.

i’ll discuss how to map back to the source language in [composable compilers](/computer-of-the-future#:~:text=composable%20compilers). for now, we’ll stick with features that don’t require mapping runtime info back to the source.
## this all sounds really cursed
<!--
> It is not so hard as we have supposed.
> —[_Jonathan Strange & Mr Norrell_](https://www.goodreads.com/book/show/14201.Jonathan_Strange_Mr_Norrell)
-->

![holy shit what the fuck is this. why is this a thing. also that sounds rather interesting.](/what-the-fuck-is-this.png)
this is cursed! it's true! working at this level stack exposes you to a whole new axis of bugs. you may discover that your program is broken only on [AMD Zen](https://hackmd.io/sH315lO2RuicY-SEt7ynGA?view#AMD-patented-time-travel-and-dubbed-it-SpecLockMapnbspnbspnbspnbspnbspnbspnbspnbspor-how-we-accidentally-unlocked-rr-on-AMD-Zen), or that it breaks when using [interruptible atomic accesses](https://github.com/rr-debugger/rr/issues/1373#issuecomment-152128412), or that it works 
<!--
thinking at a systems level is hard. but it is [possible to learn](https://jyn.dev/technical-debt-is-different-from-technical-risk/#bad-code-misses-the-point). you just have to learn not to flinch away from things you don’t know.
-->

[ELF files]: https://en.wikipedia.org/wiki/Executable_and_Linkable_Format?wprov=sfti1#Non-Unix_adoption

---
## bibliography

- [D. R. MacIver, "This is important"](https://drmaciver.substack.com/i/145700143/you-are-in-a-box)
- [Wikipedia, "Zawinski’s Law of Software Envelopment"](https://en.wikipedia.org/wiki/Jamie_Zawinski)
- [Graydon Hoare, "Rust 2019 and beyond: limits to (some) growth."](https://graydon2.dreamwidth.org/263429.html)
- [Rich Hickey, "Simple Made Easy"](https://youtu.be/SxdOUGdseq4?si=X9OZ975hwwzZOxpo&t=346)
- [Vivek Panyam, "Parsing an undocumented file format"](https://blog.vivekpanyam.com/parsing-an-undocumented-file-format)
- [The Khronos® Group Inc, "Vulcan Documentation: What is SPIR-V"](https://docs.vulkan.org/guide/latest/what_is_spirv.html)
- [Aria Desires, "C Isn't A Language Anymore"](https://faultlore.com/blah/c-isnt-a-language/)
- [Google LLC, "Standard library: cmd.cgo"](https://pkg.go.dev/cmd/cgo)
- [Filippo Valsorda, "rustgo: calling Rust from Go with near-zero overhead"](https://words.filippo.io/rustgo/)
- [WebAssembly Working Group, “WebAssembly”](https://webassembly.org/)
- [The Bytecode Alliance, "The WebAssembly Component Model"](https://component-model.bytecodealliance.org/)
- [Josh Triplett, "crABI v1"](https://github.com/rust-lang/rfcs/pull/3470)
- [Robert Lechte, “Programs are a prison: Rethinking the fundamental building blocks of computing interfaces”](https://web.archive.org/web/20210121181531/https://djrobstep.com/posts/programs-are-a-prison)
- [Wikipedia, “Executable and Linkable Format”](https://en.wikipedia.org/wiki/Executable_and_Linkable_Format?wprov=sfti1)
- [DWARF Debugging Information Format Committee, “DWARF Debugging Standard”](https://dwarfstd.org)
- [Robert O’Callahan et al., “RR”](https://rr-project.org/)
- [“ldd(1)”](https://man7.org/linux/man-pages/man1/ldd.1.html)
- [The strace developers, “strace: linux syscall tracer](https://strace.io)
- [Python Software Foundation, “`pdb` — The Python Debugger“](https://docs.python.org/3/library/pdb.html)
- [Derek Parker, “Delve”](https://github.com/go-delve/delve)
- [Susanna Clark, *Jonathan Strange & Mr Norrell*](https://www.goodreads.com/book/show/14201.Jonathan_Strange_Mr_Norrell)

[^1]: blog post forthcoming
[^2]: ELF is the default executable format on nearly every modern OS besides MacOS and Windows.
