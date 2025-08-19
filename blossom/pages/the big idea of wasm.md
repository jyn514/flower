+++
date = 2024-12-01
draft = true
+++

**WASM is a reimagining of the 50-year-old UNIX linking model**

- solve M\*N problem for executables: allow you to combine many different source languages across many different runtime platforms
	- similar in concept to other bytecodes like the JVM, CLR, p-code
- solve runtime isolation for languages not designed with a runtime in mind
	- similar to sandboxing (docker, pledge(), process isolation), but platform independent
	- works at the level of "modules", not processes. similar to v8 isolates but more general.
	- https://dl.acm.org/doi/10.1145/3477113.3487272 / https://discord.com/channels/736240421398904903/778387904925794344/1343381531984400465
- components are "typed dynamic linking"
	- self describing
	- address space isolation between components, shared memory within components
	- can be recombined
	- can imagine this being extended to support reflection
- what does this let you do?
	- plugins can be written in any language, not just Lua or the vimscript you hard-coded into your interpreter
	- combining languages is *much* easier - no longer [limited to the C ABI](https://faultlore.com/blah/c-isnt-a-language/), and you get all languages "for free" without having to write new wrappers for each
		- can imagine WASM ABI being extended to support unwinding, RAII
	- my pipe dream is to have an equivalent of powershell for WASM, where you can call an arbitrary function that's been loaded and it just *works* because it's using wasm components
