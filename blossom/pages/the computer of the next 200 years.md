---
title: the computer of the next 200 years
date: 2025-06-28
draft: true
#description: ""
---
> Google has a computer, and I wish I could rent time on it. GCE+Kubernetes means I can rent hundreds of pocket calculators (that are simulated on the computer), network them together and try to simulate having a computer.—[halvarflake](https://x.com/halvarflake/status/1248022778630021120)

my friends and i have a project we simply call The Work. The Work is amorphous; it is a collection of so many ideas it is hard to hold them all in our heads at once. it goes like this.
## [the seven laws of sane personal computing](https://www.loper-os.org/?p=284)
1. **[Obeys](https://www.loper-os.org/?p=215)** operator
2. **[Forgives](https://www.loper-os.org/?p=249)** mistakes
3. **[Retains](https://www.loper-os.org/?p=231)** knowledge
4. **[Preserves](https://www.loper-os.org/?p=256)** meaning
5. **[Survives](https://www.loper-os.org/?p=267)** disruptions
6. **[Reveals](https://www.loper-os.org/?p=273)** purpose
7. **[Serves](https://www.loper-os.org/?p=278)** loyally
an example of this is [linear](https://linear.app/), which does a shockingly good job of "doing what you expect" compared to, for example, github issues.
## technology from the past, come to save the future from itself
### Reveals purpose and Preserves meaning
that list was put together by Stanislav. Stanislav is an old-school LISP machine enthusiast; think [Symbolics](https://en.wikipedia.org/wiki/Symbolics). Symbolics machines have some fascinating features that are very rare in most modern programming environments. for example, all code (including the operating system!) is available on your machine; it's kinda like having the source code in `node_modules/` for your whole system, except that you can actually edit the code and your computer updates in real-time. there's no concept of a system restart, or of a "debug symbols package", because executable files are LISP code.
### Retains knowledge
there are other fascinating artifacts of the past. searching "orthogonal persistence" on google turns up [a paper on PS-algol](https://archive.cs.st-andrews.ac.uk/papers/download/ABC+83b.pdf) subtitled "a language for persistent programming". the thesis of this paper is that writing de/serialization code over and over is dumb and we should stop doing it. in particular, their language supports serializing data to a database just as easily as writing it to memory. this is not an academic idea; [MUMPS](https://en.wikipedia.org/wiki/MUMPS), the language powering your [health records](https://ieeexplore.ieee.org/document/7419820#:~:text=MUMPS%20in%20your%20medical%20history) and [banks](https://www.datasciencecentral.com/mumps-the-most-important-database-you-probably-never-heard-of/), has orthogonal persistence. [Bank Python] also has orthogonal persistence. think about that for a second—in PS-algol, in MUMPS, in Bank Python, *all programs are crash-consistent*. the language comes with transaction processing. [your email inbox never gets corrupted](https://danluu.com/file-consistency/) because there are no partial writes.
### Survives disruptions
[Tomorrow Corporation tech demo](https://www.youtube.com/watch?v=72y2EC5fkcE)
### build systems
some ideas are only at big companies. Google, Amazon, and Facebook all have [distributed hermetic builds with remote caching](https://medium.com/better-programming/from-blaze-to-buck2-a-brief-history-of-modern-monorepo-build-systems-563becbcb987). as result, builds are near-instant because nearly all builds are incremental and reuse existing cache. the downside is that your whole build system has to be reimplemented in [starlark](https://starlark-lang.org/) and there is very poor support for external dependencies. the closest thing to this that doesn't require rewriting the world is [nix](https://nix.dev/index.html), but it still requires packaging every piece of software in the nix language, and you cannot upload your local builds; the caching is one-way. nix does have the advantage of extremely good support for external dependencies.
### deploying code
how do these companies deploy code? in [Bank Python], it ends up in prod as soon as a reviewer approves it; there is no intermediate "deploy" step. this works because the job runner is a combined build runner and service scheduler (the post author describes it as "mega Jenkins combined with a mega systemd"). configuration is done with INI files; very little programming experience is required. Google does something a little more complicated; a service called [Rapid](https://sre.google/sre-book/release-engineering/#:~:text=Rapid%20system) drives various release tooling; either releasing immediately, or using a staged rollout with [Sisyphus](https://sre.google/sre-book/release-engineering/#:~:text=Sisyphus). similarly, release configuration is controlled by the project owners, not release engineers.

[Bank Python]: https://calpaterson.com/bank-python.html
## operators, not "programmers" and "users"
one of the recurring themes in these posts i link is that the distinction between "programmers" and "users" is very silly. the Bank Python author:
> Any new software solution is going to be compared with MS Excel and if the result is unfavourable people will often just use continue to use Excel instead. Many, many technologists have taken one look at an existing workflow of spreadsheets, reacted with performative disgust, and proposed the trifecta of microservices, Kubernetes and something called a "service mesh".

> This kind of Big Enterprise technology however takes away that basic agency of those Excel users, who no longer understand the business process they run and now have to negotiate with [ludicrous technology dweebs](https://www.youtube.com/watch?v=y8OnoxKotPQ) for each software change.

Stanislav, the LISP enthusiast:
> the distinction between "user" and "programmer" is an artifact of our presently barely-programmable and barely-usable computing systems.  I would like to use the neutral word "operator" instead.

spreadsheets are *extremely good* at uniting users and programmers because they are [malleable](https://nothingisnttrivial.com/vines.html). [situated software](https://gwern.net/doc/technology/2004-03-30-shirky-situatedsoftware.html) does not need to scale. in the pursuit of generality, we have taken away the ability to be specific, to allow people to solve their own problems.

my goal is not to make my workflow smoother. my goal is to make my workflow so smooth that i can teach it to people without programming experience in a day, so they can write their own software without needing me.
## ok but how do we build this
i know a bunch of projects that start with "computing is broken, which is why we have built the the Perfect Platform/Language/Product that will solve all your problems forever". this doesn't work because [there is never a final programming language](https://irenes.space/leaves/2024-09-29-technology-community-idealism) and you cannot get the whole world to switch over at once. this is why [Unison](https://www.unison-lang.org/) won't take over the world, why we won't standardize on [Inferno](https://github.com/inferno-os/inferno-os) and [Plan9](https://9p.io/plan9/), why there will never be one unified package manager, why [IPv6 will never take over the world](https://apenwarr.ca/log/20200708#:~:text=two%20internets?).

instead, you have to find a way for people to gradually adopt your tools, and to have them be useful without rewriting the world.
### debugging
people are working on adding [C Type Format](https://lwn.net/Articles/795564/) support to ELF toolchains, which would make debug info small enough to embed into every program, without need for separate debuginfo packages. i don't have much to say here other than to wish them luck; i think this is both high impact and very practically achievable.
### 
## where do we go from here?
this post is titled "the computer of the next 200 years" because that's about how long i expect it to take to build this system. if you join us, it might be faster.

the grand lesson of Rust, in many ways, is that [we keep forgetting already-learned lessons](http://venge.net/graydon/talks/intro-talk-2.pdf#page=7). it's time to remember.
<!--computers should do what you tell them to. computers should be predictable, reliable, safe, secure. computers should not have a distinction between users and programmers. -->