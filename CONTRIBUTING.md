## building flower

hi! thank you for wanting to hack on flower ^^

be aware that flower is still very much a work in progress. for now, please please do [reach out to jyn][email] if you want to get started, i'm happy to answer questions about how things work.

[email]: mailto:flower@jyn.dev

### setup

1. [Install clojure](https://clojure.org/guides/install_clojure).
   Clojure is broken in many Linux package distributions. Prefer the official installer if at all possible.

1. Install GraalVM using `scripts/install-graal.clj`.
  - If you don't use the script, download from https://www.graalvm.org/latest/getting-started/, make sure this ends up in `target/graalvm-jdk-24/`; that's expected by the `.envrc` file

1. Install [`direnv`](https://direnv.net/).

1. Run `direnv allow`, which adds the things in `.envrc`.
  This lets you run `flower` without having to qualify it as `./target/flower`.
  It also makes the default Java be Graal, which `build.clj` expects.

1. Run `clojure -T:build manifest`, which creates a list of all the defaults that get embedded in the flower binary.

### running flower

You have several options now:
- Run flower as a clojure script: `clojure -M -m flower.main`. This works ok, but you cannot be in any directory other than the flower repo (so e.g. you can't run it on a test site). It also has very slow startup times.
- Run flower as a jar file: `clojure -T:build uberjar && flower` (the `flower` alias was set up by `.envrc`). This lets you run flower on directories outside the flower/ repo (in that case, use `java -jar target/flower.jar`). Unfortunately, it's still slow.
- Run flower as a Graal native binary: `clojure -T:build native && flower`. This is extremely fast to run, but has horribly slow build times (about 64 seconds on my Ryzen 7 7700X).

Choose the right one for whatever you're doing. I normally use the Graal binary because my site has many pages and I don't want to pay the JVM startup time a dozen times.


### running tests

`clojure -M:test`. This has a fair amount of startup time; add `--watch` to live-reload changes from the filesystem.

## modifying flower

flower is *mostly* a normal clojure project. The code lives in `./flower` and the entrypoint is `flower/main.clj`. `--help` output is [a work in progress](https://codeberg.org/jyn514/flower/issues/6). In the meantime, look at `flower.main/dispatch-table` for a list of commands and arguments.

Run `clojure -X:deps aliases` to see a list of build tasks.
