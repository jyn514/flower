---
title: "you are in a box"
date: 2025-07-14
description: "your data is trapped inside the box that is your program. you can only see what the program author exposes."
taxonomies:
 tags: ["ideas"]
 computer-of-the-future: ["1"]
extra:
  category: "principles"
  toc: 3
#  audience: "everyone"
#  unlisted: true
#  stub: true
---
> **You are trapped in a box. You have been for a long time.**  
> —[*D. R. MacIver*](https://drmaciver.substack.com/i/145700143/you-are-in-a-box)

> **Every program attempts to expand until it can read mail. Those programs which cannot so expand are replaced by ones which can.**  
> —[_Zawinski's Law of Software Envelopment_](https://en.wikipedia.org/wiki/Jamie_Zawinski#Zawinski's_Law)
## switching costs and growth
most tools simultaneously think too small and too big. “i will let you do anything!”, they promise, “as long as you give up your other tools and switch to me!”

this is true of languages too. any new programming language makes an implicit claim that “using this language will give you an advantage over any other language”, at least for your current problem.

once you start using a tool for one purpose, due to switching costs, you want to keep using that tool. so you start using it for things that wasn’t designed for, and as a result, tools tend to grow and grow and grow until they [stagnate][tech-risk]. in a sense, we have replicated the business boom-and-bust cycle in our own tools.

[tech-risk]: /technical-debt-is-different-from-technical-risk/#what-to-do-about-risk
<!-- /technical-debt-is-different-from-technical-risk/#technical-risk-means-a-program-is-hard-to-modify -->
<!--## escaping the boom and bust cycle-->
## interoperability
there are two possible ways to escape this trap. the first is to [impose a limit on growth](https://graydon2.dreamwidth.org/263429.html), so that tools can’t grow until they bust. this makes a lot of people very unhappy and is generally regarded as a bad idea.

the second is to decrease switching costs. by making it easier to switch between tools, or to interoperate between multiple tools in the same system, there is not as much pressure to have “one big hammer” that gets used for every problem.
### back-compat
tools and languages can decrease switching costs by keeping backwards compatibility with older tools, or at least being close enough that they’re [easy to learn][Simple Made Easy] for people coming from those tools. for example, ripgrep has almost exactly the same syntax as GNU grep, and nearly every compiled language since C has kept the curly braces.

<!--note that you can think of different versions of a tool as having to interoperate with themselves, so you may see these techniques used even when the authors did not intend a clear interface boundary.-->

[Simple Made Easy]: https://youtu.be/SxdOUGdseq4?si=X9OZ975hwwzZOxpo&t=346
### standardization
tools can also collaborate on standards that make it easier to interoperate. this is the basis of nearly every network protocol, since there's no guarantee that the same tool will be on the other side of the connection. to some extent this also happens for languages (most notably for C), where a language specification allows multiple different compilers to work on the same code.

this has limitations, however, because the tool itself has to want (or be forced) to interoperate. for example, the binary format for CUDA (a framework for compiling programs to the GPU) is undocumented, so you're stuck with [reverse engineering](https://blog.vivekpanyam.com/parsing-an-undocumented-file-format) or [re-implementing](https://docs.vulkan.org/guide/latest/what_is_spirv.html) the toolchain if you want to modify it.
### FFI
the last "internal" way to talk to languages is through a "foreign function interface", where functions in the same process can call each other cheaply. this is hard because each language has to go [all the way down to the C ABI](https://faultlore.com/blah/c-isnt-a-language/) before there's something remotely resembling a standard, and because two languages may have incompatible runtime properties that make FFI [hard](https://pkg.go.dev/cmd/cgo#hdr-Passing_pointers) or [slow](https://words.filippo.io/rustgo/#why-not-cgo). languages that do encourage FFI often require you to write separate bindings for each program: for example, Rust requires you to write `extern "C"` blocks for each declaration, and python requires you to do that and also write wrappers that translate C types into python objects.

i won't talk too much more about this—the work i'm aware of in this area is mostly around [WASM](https://webassembly.org/) and [WASM Components](https://component-model.bytecodealliance.org/), and there are also some efforts to [raise the baseline for ABI](https://github.com/rust-lang/rfcs/pull/3470) above the C level.
<!--for instance, take the Go language. Go has many strengths—a performant green threads runtime, excellent devtools, static binaries. in exchange, you are locked into the Go ecosystem. unlike other languages (Python, Rust, JS), calls between Go and other languages are [hard](https://pkg.go.dev/cmd/cgo#hdr-Passing_pointers) and have a [high performance overhead](), which means that most libraries you might want to use have to be rewritten into Go. Compare this to, for example, Lua, which is intentionally designed to be easy to embed into larger applications due to its small language size and minimal runtime requirements.-->
### IPC
#### Unix shells
another approach is to compose tools. the traditional way to do this is to have a shell that allows you to freely compose programs with IPC. this does unlock a lot of freedom! IPC allows programs to communicate across different languages, different ABIs, and different user-facing APIs. it also unlocks 'ad-hoc' programs, which can be thought of as [situated software](https://gwern.net/doc/technology/2004-03-30-shirky-situatedsoftware.html) for developers themselves. consider for example the following shell pipeline:
```bash
git verify-pack -v \
        $(git rev-parse --git-common-dir)/objects/pack/pack-*.idx \
    | sort -k3 -n | cut -f1 -d' ' \
    | while read i; do git ls-tree -r HEAD  | grep "$i"; done \
    | tail
```
this [shows the 10 largest files in the git history for the current repository](https://askubuntu.com/questions/1259129/). let's set aside the readability issues for now. there are a lot of good ideas here! note that programs are interacting freely in many ways:
- the output of `git rev-parse` is passed as a CLI argument to `git verify-pack`
- the output of `git verify-pack` is passed as stdin to `sort`
- the output of `cut` is interpreted as a list and programmatically manipulated by `while read`. this kind of meta-programming is common in shell and has concise (i won't go as far as "simple") syntax.
- the output from the meta-programming loop is itself passed as stdin to the `tail` command

the equivalent in a programming language without spawning a subprocess would be very verbose; not only that, it would require a library for the git operations in each language, bringing back the FFI issues from before (not to mention the hard work designing "cut points" for the API interface[^1]). this shell program can be written, concisely, using only tools that already exist.

note though that the data flow here is a DAG: pipes are one-way, and the CLI arguments are evaluated before the new program is ever spawned. as a result, it’s not possible to do any kind of content negotiation (other than the programmer hard-coding it with CLI args; for example tools commonly have `--format=json`).

the downside of this approach is that the interface is completely unstructured; programs work on raw bytes, and there is no common interface. it also doesn't work if the program is interactive, unless the program deliberately exposes a way to query a running server (e.g. `tmux list-panes` or `nvim --remote`). let's talk about both of those.
#### structured IPC
[powershell](https://learn.microsoft.com/en-us/powershell/scripting/lang-spec/chapter-04?view=powershell-7.5), and more recently, [nushell](https://www.nushell.sh/book/types_of_data.html), extend traditional unix pipelines with structured data and a typesystem. they have mechanisms for parsing arbitrary text into native types, and helper functions for common data formats.

this is really good! i think it is the first major innovation we have seen in the shell language in many decades, and i'm glad it exists. but it does have some limitations:
- there is no interop between powershell and nushell.
- there is no protocol for programs to self-describe their output in a schema, so each program's output has to be special-cased by each shell.
	- powershell side-steps this by [building on the .NET runtime](https://learn.microsoft.com/en-us/powershell/scripting/overview?view=powershell-7.5#scripting-language), and having native support for programs which emit .NET objects in their [output stream](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_output_streams?view=powershell-7.5). but this doesn't generalize to programs that don't run on the [CLR](https://learn.microsoft.com/en-us/dotnet/standard/clr).
- there is no stability guarantee between versions of a program. even tools with semi-structured JSON output are free to change the structure of the JSON, breaking whatever code parses it.

and it is very hard to fix these limitations because there is no "out-of-band" communication channel that programs could use to emit a schema; the closest you could get is a "standardized file descriptor number", but that will lock out any program that happens to already be using that FD. we have limited kinds of reflection in the form of shell completion scripts, but they're not standardized: there's not a standard for the shell to query the program, and there's not a standard for the format the program returns. the CLI framework inside the program often *does* have a schema and reflection capabilities, but they're discarded the second you go over an IPC boundary.
#### RPC
how do you get a schema? well, you establish in-band communication. RPC is theoretically about "remote" procedure calls, but it's just as often used for local calls. the thing that really distinguishes it is that it's in-band: you have a defined interface that emits structured information.

RPC works really quite well! there are frameworks for [forwards- and backwards-compatible RPC](https://protobuf.dev/); types and APIs are shared across languages; and interop for a new language only requires writing bindings between that language and the on-wire format, not solving a handshake problem between all pairs of languages nor dropping down to the C ABI.

the main downside is that it is a lot of work to add to your program. you have to extensively modify your code to fit it into the shape the framework expects, and to keep it performant you sometimes even have to modify the in-memory representation of your data structures so they can live in a contiguous buffer. you can avoid these problems, but only by giving up performance when deserializing (e.g. by parsing JSON at runtime).
## you are trapped in a box
all these [limitations](/operators-not-users-and-programmers#the-user-programmer-distinction) are because [programs are a prison](https://web.archive.org/web/20210121181531/https://djrobstep.com/posts/programs-are-a-prison). your data is trapped inside the box that is your program. the commonality between all these limitations is that they require work from the program developer, and without that work you're stuck. even the data that leaves the program has to go through the narrow entrances and exits of the box, and [anything that doesn't fit is discarded](https://siderea.dreamwidth.org/1540620.html).

some languages try to make the box bigger—interop between Java, Kotlin, and Clojure is comparatively quite easy because they all run on the JVM. but at the end of the day the JVM is another box; getting a non-JVM language to talk to it is hard.

some languages try to make the box extensible—LISPs, and especially Racket, try to make it very easy to build new languages inside the box. but getting non-LISPs inside the box is hard.

some tools try to give you individual features—smalltalk gets you orthogonal persistence; pluto.jl gets you a “terminal of the future”; rustc gets you sub-process incremental builds. but all those features are inside a box.

often, tools don’t even try. vendor lock in, subtle or otherwise, is everywhere around us. tools with this strategy tend to be the largest, since they have both the biggest budget and the greatest incentive to prevent you from switching tools.

and always, always, always, you are at the mercy of the program author.

in my next post, i will discuss how we can escape this box.

---
## bibliography

- [D. R. MacIver, “This is important”](https://drmaciver.substack.com/i/145700143/you-are-in-a-box)
- [Wikipedia, “Zawinski’s Law of Software Envelopment”](https://en.wikipedia.org/wiki/Jamie_Zawinski)
- [Graydon Hoare, “Rust 2019 and beyond: limits to (some) growth.”](https://graydon2.dreamwidth.org/263429.html)
- [Rich Hickey, “Simple Made Easy”](https://youtu.be/SxdOUGdseq4?si=X9OZ975hwwzZOxpo&t=346)
- [Vivek Panyam, “Parsing an undocumented file format”](https://blog.vivekpanyam.com/parsing-an-undocumented-file-format)
- [The Khronos® Group Inc, “Vulcan Documentation: What is SPIR-V”](https://docs.vulkan.org/guide/latest/what_is_spirv.html)
- [Aria Desires, “C Isn’t A Language Anymore”](https://faultlore.com/blah/c-isnt-a-language/)
- [Google LLC, “Standard library: cmd.cgo”](https://pkg.go.dev/cmd/cgo)
- [Filippo Valsorda, “rustgo: calling Rust from Go with near-zero overhead”](https://words.filippo.io/rustgo/)
- [WebAssembly Working Group, “WebAssembly”](https://webassembly.org/)
- [The Bytecode Alliance, “The WebAssembly Component Model”](https://component-model.bytecodealliance.org/)
- [Josh Triplett, “crABI v1”](https://github.com/rust-lang/rfcs/pull/3470)
- [Clay Shirky, "Situated Software"](https://gwern.net/doc/technology/2004-03-30-shirky-situatedsoftware.html)
- [Microsoft, "PowerShell 7.5: 4. Types"](https://learn.microsoft.com/en-us/powershell/scripting/lang-spec/chapter-04?view=powershell-7.5)
- [Microsoft, "PowerShell 7.5: What is PowerShell?"](https://learn.microsoft.com/en-us/powershell/scripting/overview?view=powershell-7.5)
- [Microsoft, "PowerShell 7.5: about_Output_Streams"](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_output_streams?view=powershell-7.5)
- [Microsoft, ".NET Execution model: Common Language Runtime (CLR) overview"](https://learn.microsoft.com/en-us/dotnet/standard/clr)
- [Nushell Project, "Nu Fundamentals: Types of Data"](https://www.nushell.sh/book/types_of_data.html)
- [Google LLC, “Protocol Buffers”](https://protobuf.dev/)
- [Robert Lechte, “Programs are a prison: Rethinking the fundamental building blocks of computing interfaces”](https://web.archive.org/web/20210121181531/https://djrobstep.com/posts/programs-are-a-prison)
- [Siderea, "Procrustean Epistemologies"](https://siderea.dreamwidth.org/1540620.html)

[^1]: blog post forthcoming
