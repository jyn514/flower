## building flower

hi! thank you for wanting to hack on flower ^^

be aware that flower is still very much a work in progress. for now, please please do [reach out to jyn][email] if you want to get started, i'm happy to answer questions about how things work.

[email]: mailto:flower@jyn.dev

### setup

1. Run `mkdir -p target/classes`

1. [Install clojure](https://clojure.org/guides/install_clojure).
   Clojure is broken in many Linux package distributions. Prefer the official installer if at all possible.

1. Install GraalVM using `scripts/install-graal.clj`.
    - If you don't use the script, download from [the official installer](https://www.graalvm.org/latest/getting-started/). Make sure this ends up in `target/graalvm-jdk-24/`; that's expected by the `.envrc` file

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

`clojure -M:test`. This has a fair amount of startup time; add `--watch` to live-reload changes from the filesystem. Use `:unit` to only run unit tests (integration tests are *reasonably* fast, but not quite fast enough to run on every save).

### running ci

First, [install woodpecker-cli](https://github.com/woodpecker-ci/woodpecker/releases/latest#:~:text=Assets).
(There is also a [nix package](https://search.nixos.org/packages?channel=unstable&show=woodpecker-cli).)
Then, run `./scripts/run-ci.sh`.

By default this uses the code in your local checkout.
Pass `--remote` to use the latest commit pushed to your branch.
See also the "Debug" pane on pushed branches; [example](https://ci.codeberg.org/repos/15384/pipeline/10/debug).

## modifying flower

### what to work on

see https://codeberg.org/jyn514/flower/milestones. in general, please check with me before doing anything too complicated.

### editing code

flower uses [`deps.edn`](https://clojure.org/reference/deps_edn) for dependency management.
The code lives in `./flower` and the entrypoint is `flower/main.clj`.
Run `flower help` for a list of built-in commands.
Run `clojure -X:deps aliases` to see a list of build tasks.

### compile phases

flower is different from a normal clojure project because it has to think much more about *phases*. flower code can run in one of 5 possible phases:
- compile time. This is run by `clojure -T:build uberjar`, and creates a bunch of .class files. Nothing too complicated here.
- native-build time. This is when `native-image` creates a [Graal Native](https://www.graalvm.org/) binary. We use the `graal-build-time` library to initialize all clojure modules into the image heap. In other words, all `def`s in the clojure source are run during a `native-image` build, not at runtime. As a result, there are several restrictions here:
  - `def`s must ensure all JVM green threads they spawn are shut down by the time initialization finishes.
  - `def`s cannot spawn [Agents](https://clojure.org/reference/agents). Doing so spawns a thread pool. Calling `(shutdown-agents)` will let the code compile, but breaks at runtime because all future agents will error.
  - `def`s cannot run `(babashka.ps/shell)` to spawn processes. Doing so spawns an agent.
  - `def`s should not contain any platform-specific logic. Such logic executes on the host machine, not the target.
  - `def`s should be careful not to depend on any global `*bindings*`, which will likely not be set.
- runtime in a Graal native binary. Runs on the target machine, which may be different than the host machine. Be careful about platform-specific code.
- bound into an SCI interpreted environment. In this environment, *host* code can use any functions they like (please track your file dependencies!), but *guest* code can only use functions bound into the SCI context.
  - Additionally, creating an SCI context needs to consider order of initialization. For example, the following code is wrong:
    ```clj
    (declare my-fn)
    (def cx (sci/init {:bindings 'my-fn my-fn}))
    (defn my-fn [] true)
    ```
    This binds an uninitialized `nil` value into the SCI guest.
    The correct thing is to switch the order of `my-fn` and `def cx`, or if that's not possible, turn `cx` into a `defn` instead of a `def`.
- live-reloaded from a test function. This is the environment we have the least control over; be careful.
    - If you need to depend on a global `^:dynamic` variable from a `def`, make sure to bind it in `tests.edn:bindings`.
    - If you load any guest code in `defaults/`, make sure it obeys normal clojure rules, not the relaxed rules in the guest environment. For example, it must replace `-` in file names with `_`, and all files must have a correct `ns` directive.
    - If you do anything funny with namespaces in `flower.eval/sci-defaults`, and need to depend on it in a test context, add a "shim" function in `test/flower` (or whatever the appropriate path is). See `test/flower/fs.clj` for an example.
