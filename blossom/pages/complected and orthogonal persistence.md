---
title: complected and orthogonal persistence
date: 2025-06-30
#draft: true
description: how hard is it to save and restore program state?
slug: complected-and-orthogonal-persistence
taxonomies:
  computer-of-the-future:
    - 4
  tags:
    - ideas
extra:
    category: tools
---
[protobuf]: https://protobuf.dev/
> _**Everything Not Saved Will Be Lost**_—Ancient Nintendo Proverb
## persistence is hard
say that you are writing an editor. you don't want to lose people's work so you implement an "autobackup" feature, so that people can restore their unsaved changes if the program or whole computer crashes.

[implementing this is hard](https://danluu.com/file-consistency/)! the way i would do it is to serialize the data structures using something like [bincode](https://docs.rs/bincode/latest/bincode/) and then write them to an SQLite database so that i get crash-consistency. there are other approaches with different tradeoffs.
## languages with persistence

[PS-algol]: https://archive.cs.st-andrews.ac.uk/papers/download/ABC+83b.pdf

this [1983 paper][PS-algol] asks: why are we spending so much time rewriting this in applications, instead of doing it once in the runtime? it then introduces a language called "PS-algol", which supports exactly this through a library. note that arbitrary data types in the language are supported without the need for writing user code.

it turns out that this idea is already being used in production. not in that form—people don’t use Algol anymore—but the idea is the same. M (better known as [MUMPS](https://en.wikipedia.org/wiki/MUMPS)), [Bank Python](https://calpaterson.com/bank-python.html), and the [IBM i](https://www.devever.net/~hl/f/as400guide.pdf)[^4] are still used in healthcare, financial, and insurance systems, and they work exactly like this[^5]. here is a snippet of M that persists some data to a database:
```mumps
 SET ^table("column","primary key")="value"
```
and here is some Bank Python that does the same:
```python
db["/my_table"] = Table(
  [("etf", str), ("shares", float)],
  ["SPY", 1200.0]
)
```
and finally some COBOL: [^3]
```COBOL
IDENTIFICATION DIVISION.
PROGRAM-ID. FILES.
ENVIRONMENT DIVISION.
  INPUT-OUTPUT SECTION.
	FILE-CONTROL.
	  SELECT TRANSACTIONS ASSIGN TO 'transactions.txt'
	  ORGANIZATION IS SEQUENTIAL.
DATA DIVISION.
  FILE SECTION.
	FD TRANSACTIONS.
	01 TRANSACTION-STRUCT.
	  02 UID PIC 9(5).
	  02 DESC PIC X(25).
WORKING-STORAGE SECTION.
	01 TRANSACTION-RECORD.
	  02 UID PIC 9(5) VALUE 12345.
	  02 DESC PIC X(25) VALUE 'TEST TRANSACTION'.
PROCEDURE DIVISION.
  OPEN OUTPUT TRANSACTIONS
	WRITE TRANSACTION-STRUCT FROM TRANSACTION-RECORD
  CLOSE TRANSACTIONS
  STOP RUN.
```
note how in all of these, the syntax for persisting the data to disk is essentially the same as persisting it to memory (in MUMPS, persisting to memory is exactly the same, except you would write `SET table` instead of `SET ^table`).

if you don't require the runtime to support all datatypes, there are frameworks for doing this as a library. [protobuf] and [flatbuffer] both autogenerate the code for a restricted set of data types, so that you only have to write the code invoking it.

[flatbuffer]: https://flatbuffers.dev/
## orthogonal persistence
the thing these languages and frameworks have in common is that the persistence is part of the program source code; either you have to choose a language that already has this support, or you have to do extensive modifications to the source code to persist the data at the right points. i will call this kind of persistence *complected persistence* because they tie together the business logic and persistence logic (see [Simple Made Easy](https://www.infoq.com/presentations/Simple-Made-Easy/) by Rich Hickey for the history of the word "complect").

there is also *orthogonal persistence*. ["orthogonal persistence"](https://en.wikipedia.org/wiki/Persistence_(computer_science)#Orthogonal_or_transparent_persistence) means your program's state is saved automatically without special work from you the programmer. in particular, the serialization is managed by the OS, database, or language runtime. as a result, you don't have to care about persistence, only about implementing business logic; the two concerns are [orthogonal](https://en.wikipedia.org/wiki/Orthogonality#Computer_science).

orthogonal persistence is more common than you might think. some examples:
- [hibernation](https://en.wikipedia.org/wiki/Hibernation_(computing)) (suspend to disk). first invented in 1992 for the Compaq [LTE Lite](https://en.wikipedia.org/wiki/Compaq_LTE#LTE_Lite). Windows has this on by default since Windows 8 (2012). MacOS has had it on by default since OS X 10.4 (2005).
- virtualized hibernation in hypervisors like VirtualBox and VMWare (usually labeled "Save the machine state" or something similar)
## how far can we take this?
these forms of orthogonal persistence work on the whole OS state. you could imagine a version that works on individual processes: swap the process to disk, restore it later. the kernel kinda already does this when it does scheduling. you can replicate it in userspace with [telefork], which even lets you spawn a process onto another machine.

[telefork]: https://thume.ca/2020/04/18/telefork-forking-a-process-onto-a-different-computer/

but the rest of the OS moves on while the process is suspended: the files it accesses may have changed, the processes it was talking to over a socket may have exited. what we want is to snapshot the process state: whatever files on disk stay on disk, whatever processes it was talking to continue running. this allows you to rewind and replay the process, as if the whole thing were running in a database transaction.

what do we need in order to do that?
- a filesystem that supports atomic accesses, snapshots, and transaction restarts, such as [ZFS].
- a runtime that supports detailed tracking and replay of syscalls, such as [rr]. this works by intercepting syscalls with [ptrace()](https://man7.org/linux/man-pages/man2/ptrace.2.html), among other mechanisms, and does not require any modifications to the program executable or source code.
- a sandbox that prevents talking to processes that weren’t running when the target process was spawned (unless those processes are also in the sandbox and tracked with this mechanism), such as [bubblewrap].

[ZFS]: https://en.wikipedia.org/wiki/ZFS#Snapshots_and_clones
[rr]: https://rr-project.org/
[bubblewrap]: https://github.com/containers/bubblewrap

effectively, we are turning syscalls into [capabilities], where the capabilities we give out are “exactly the syscalls the process made last time it spawned”.

[capabilities]: http://habitatchronicles.com/2017/05/what-are-capabilities/

note how this is possible to do today, with existing technology and kernel APIs! this doesn’t require building an OS from scratch, nor rewriting all code to be in a language with tracked effects or a capability system. instead, by working at the syscall interface[^6] between the program and the kernel, we can build a highly general system that applies to all the programs you already use.
## “but why?”
note that this involves 3 different levels of tracking, which we can think of in terms of progressive enhancement:
1. features you can get just by recording and replaying the whole process ("tracking between processes")
2. features you can get by replaying from a specific point in the process ("tracking within a process")
3. features you can only get with source code changes, by allowing the process to choose where it should be restored to ("tracking that needs source code changes")

the editor example i gave at the beginning refers to 3; but you can get really quite a lot of things just with 1 (tracked record/replay and transactional semantics). for example, here are some tools that would be easy to build on top:
### needs tracking between processes
- collaborative terminals, where you can “split” your terminal and hand a sandboxed environment of *your personal computer* to a colleague so they can help you debug an issue. this is more general than OCI containers because you don't need to spend time creating a dockerfile that reproduces the problem. this is more general than [`rr pack`](https://robert.ocallahan.org/2017/09/rr-trace-portability.html) because you can edit the program source to add printfs, or change the input you pass to it at runtime.
- “save/undo for your terminal”, where you don’t need to add a [`--no-preserve-root`](https://www.gnu.org/software/coreutils/manual/html_node/Treating-_002f-specially.html) flag to `rm`, because the underlying filesystem can just restore a snapshot. this generalizes to any command—for example, you can build an arbitrary `git undo` command that works even if installed after the data is lost, which is [not possible today](https://blog.waleedkhan.name/git-undo/). note that this can undo by-process, not just by point-in-time, so it is strictly more general than FS snapshots.
- query which files on disk were modified the last time you ran a command. for example you could ask “where did this `curl | sh` command install its files?”. the closest we have to this today is [`dpkg --listfiles`](https://man7.org/linux/man-pages/man1/dpkg.1.html#:~:text=listfiles), which only works for changes done by the package manager.
### needs tracking within a process
- [asciinema](https://asciinema.org/), but you actually run the process instead of building your own terminal emulator. this also lets you edit the recording live instead of having to re-record from scratch.
- the [“post-modern build system”](https://jade.fyi/blog/the-postmodern-build-system/#limits-of-execve-memoization) (also needs a [salsa](https://salsa-rs.netlify.app/)-like [red-green system](https://rustc-dev-guide.rust-lang.org/queries/incremental-compilation-in-detail.html#improving-accuracy-the-red-green-algorithm))
### needs source code changes
- “save/undo for your program”, where editors and games can take advantage of cheap snapshots to use the operating system's restore mechanism instead of building their own.

this is not an exhaustive list, just the first things on the top of my head after a couple days of thinking about it. what makes this orthogonal persistence system so useful is that all these tools are near-trivial to build on top: most of them could be done in shell scripts or a short python script, instead of needing a team of developers and a year.
## isn’t this horribly slow?
not inherently. “turning your file system into a database“ is only as slow as submitting the query is[^1]—and that can be quite fast when the database runs locally instead of over the network; see [sled](https://github.com/spacejam/sled?tab=readme-ov-file#performance) for an example of such a DB that has had performance tuning. rr boasts [less than a 20% slowdown](https://rr-project.org/#:~:text=slowdown). bubblewrap uses the kernel’s native namespacing and to my knowledge imposes no overhead.

now, the final system needs to be designed with performance in mind and then carefully optimized but, you know. that's doable.
## does this work across versions of my program?
kinda. there are two possible ways to implement intra-process persistence (i.e. "everything other than disk writes").
1. take a snapshot of the memory, registers, and kernel state (e.g. file descriptors). this is how [telefork] works. this only works with a single version of the executable; any change, even LTO without changing source code, will break it.
2. replay all syscalls done by the process. this will work across versions, as long as the program makes the same syscalls in the same order.

1 is cheap if you don't have much memory usage compared to CPU time.
2 is cheap if you don't have much CPU time compared to memory usage.
only 2 allows you to modify the binary between saving and restoring.
it's possible to do both for the same process, just expensive.
## summary
- persisting program state is hard and basically requires implementing a database
- persistence that does not require action from the program is called “orthogonal persistence”
- it is possible to build orthogonal persistence for individual processes with tools that exist today, with only moderate slowdowns depending on how granular you want to be
- there are multiple possible ways to implement this system, with different perf/generality tradeoffs
- such a system unlocks many kinds of tools by making them orders of magnitude easier to build

many of the ideas in this post were developed in collaboration with edef. if you want to see them built, consider [sponsoring her](https://github.com/sponsors/edef1c) so she has time to work on them.

---
## bibliography

- [Dan Luu, "Files are hard"](https://danluu.com/file-consistency/)
- [Ty Overby, Zoey Riordan, Victor Koenders, *bincode*](https://docs.rs/bincode/latest/bincode/)
- [Atkinson, M.P., Bailey, P.J., Chisholm, K.J., Cockshott, W.P. & Morrison, R. “PS-algol: A
Language for Persistent Programming”. In Proc. 10th Australian National Computer
Conference, Melbourne, Australia (1983) pp 70-79.](https://archive.cs.st-andrews.ac.uk/papers/download/ABC+83b.pdf)
- [Cal Paterson, "An oral history of Bank Python"](https://calpaterson.com/bank-python.html)
- [Hugo Landau, "IBM i: An Unofficial Introduction"](https://www.devever.net/~hl/f/as400guide.pdf)
- [Google LLC, "Protocol Buffers"](https://protobuf.dev/)
- [Google LLC, "FlatBuffers Docs"](https://flatbuffers.dev/)
- [Rich Hickey, "Simple Made Easy"](https://www.infoq.com/presentations/Simple-Made-Easy/)
- [Tristan Hume, "Teleforking a process onto a different computer!"](https://thume.ca/2020/04/18/telefork-forking-a-process-onto-a-different-computer/)
- [Robert O’Callahan et al., "RR"](https://rr-project.org/)
- [Simon McVittie et al., "bubblewrap"](https://github.com/containers/bubblewrap)
- [Chip Morningstar and F. Randall Farmer, "What Are Capabilities?"](http://habitatchronicles.com/2017/05/what-are-capabilities/)
- [Robert O'Callahan, "rr Trace Portability"](https://robert.ocallahan.org/2017/09/rr-trace-portability.html)
- [Free Software Foundation, Inc., "GNU Coreutils"](https://www.gnu.org/software/coreutils/manual/html_node/Treating-_002f-specially.html)
- [Waleed Khan, "git undo: We can do better"](https://blog.waleedkhan.name/git-undo/)
- [Marcin Kulik, "Record and share your terminal sessions, the simple way."](https://asciinema.org/)
- [Jade Lovelace, "The postmodern build system"](https://jade.fyi/blog/the-postmodern-build-system/#limits-of-execve-memoization)
- [Salsa developrs, "About salsa"](https://salsa-rs.netlify.app/)
- [The Rust Project contributors, "Incremental compilation in detail"](https://rustc-dev-guide.rust-lang.org/queries/incremental-compilation-in-detail.html#improving-accuracy-the-red-green-algorithm)
- [Tyler Neely, "sled - it's all downhill from here!!!"](https://github.com/spacejam/sled?tab=readme-ov-file#performance)
- [edef](https://github.com/sponsors/edef1c)
- [The PostgreSQL Global Development Group, "Reliability and the Write-Ahead Log: Write-Ahead Logging (WAL)"](https://www.postgresql.org/docs/current/wal-intro.html#WAL-INTRO)
- [Yvan Scher, "7 cobol examples with explanations."](https://medium.com/@yvanscher/7-cobol-examples-with-explanations-ae1784b4d576)
- [Robert N. M. Watson et al., "CTSRD – Rethinking the hardware-software interface for security"](https://www.cl.cam.ac.uk/research/security/ctsrd/)
- [WebAssembly Working Group, "WebAssembly"](https://webassembly.org/)
- [Rob Pike, "Systems Software Research is Irrelevant"](https://doc.cat-v.org/bell_labs/utah2000/utah2000.html)
- [System Calls Manual, "ptrace(2)"](https://man7.org/linux/man-pages/man2/ptrace.2.html)
- [dpkg suite, "dpkg(1)"](https://man7.org/linux/man-pages/man1/dpkg.1.html#:~:text=listfiles)
- [Wikipedia, "MUMPS"](https://en.wikipedia.org/wiki/MUMPS)
- [Wikipedia, "orthogonal persistence"](https://en.wikipedia.org/wiki/Persistence_(computer_science)#Orthogonal_or_transparent_persistence)
- [Wikipedia, "IBM i"](https://en.wikipedia.org/wiki/IBM_i#Technology_Independent_Machine_Interface_(TIMI))
- [Wikipedia, "ZFS"](https://en.wikipedia.org/wiki/ZFS#Snapshots_and_clones)
- [Wikipedia, "Orthogonality"](https://en.wikipedia.org/wiki/Orthogonality#Computer_science)
- [Wikipedia, "Hibernation (computing)"](https://en.wikipedia.org/wiki/Hibernation_(computing))
- [Wikipedia, "Compaq LTE Lite"](https://en.wikipedia.org/wiki/Compaq_LTE#LTE_Lite)

[^1]: one might think that you have to flush each write to disk before returning from write() in order to preserve ACID semantics. not so; this is exactly the problem that a [write-ahead-log](https://www.postgresql.org/docs/current/wal-intro.html#WAL-INTRO) fixes.

[^3]: credit [@yvanscher](https://medium.com/@yvanscher/7-cobol-examples-with-explanations-ae1784b4d576)

[^4]: the IBM i will be coming up many times in this series, particularly block terminals and the object capability model. i will glaze over parts of the system that cannot be intercepted at a syscall boundary; but that said i want to point out that [IBM POWER extensions](https://www.devever.net/~hl/f/as400guide.pdf#page=13) and [TIMI](https://en.wikipedia.org/wiki/IBM_i#Technology_Independent_Machine_Interface_(TIMI)) correspond roughly to the modern ideas of the [CHERI](https://www.cl.cam.ac.uk/research/security/ctsrd/) and [WASM](https://webassembly.org/) projects.

[^5]: jyn, you might ask, why are all these systems so old! why legacy systems? isn't there any new code with these ideas? and the answer is no. [systems research is irrelevant](https://doc.cat-v.org/bell_labs/utah2000/utah2000.html) and its new ideas, such as they are, do not make it into industry.

[^6]: on all OSes i know other than Linux, the syscall ABI is not stable and programs are expected to use libc bindings. you can do something similar by using LD_PRELOAD to intercept libc calls.
