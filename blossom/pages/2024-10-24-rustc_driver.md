---
title: building your own <code>rustc_driver</code>
date: 2024-10-24
extra:
description: what happens when you run <code>cargo clippy</code>?
taxonomies:
  tags:
    - walkthroughs
    - rust
---
## a deeper rabbit hole than expected

what happens when you run `cargo clippy`?

well, we can ask cargo what it does:
```
$ cargo clippy -v
    Checking example v0.1.0 (/home/jyn/src/example)
     Running `/home/jyn/.rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/clippy-driver /home/jyn/.rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/rustc --crate-name example --edition=2021 src/main.rs --error-format=json --json=diagnostic-rendered-ansi,artifacts,future-incompat --diagnostic-width=124 --crate-type bin --emit=dep-info,metadata -C embed-bitcode=no -C debuginfo=2 --check-cfg 'cfg(docsrs)' --check-cfg 'cfg(feature, values())' -C metadata=f3baefdd4f0d88a8 -C extra-filename=-f3baefdd4f0d88a8 --out-dir /home/jyn/.cargo/target/debug/deps -C incremental=/home/jyn/.cargo/target/debug/incremental -L dependency=/home/jyn/.cargo/target/debug/deps`
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 0.67s
```
that's kinda weird! it's running something called `clippy-driver`? and passing `bin/rustc` as an argument to it? what's going on there? is it running any other programs afterwards?

we can find that out too:
```
; strace -f -e execve cargo clippy
```
<details><summary>all of strace's output</summary>

note this is massaged slightly for clarity; the actual command i ran was
```
cargo clean; strace -f -e execve,clone3 -s 100 cargo clippy 2>&1 | sed 's/Process /\nProcess /; s/execve([^,]*, \([^]]*\).*/execve(\1])/' | rg -v 'resumed>' | rg '(\[pid[^]]*\] )?(execve.*|clone3|Process.*)' -o | sed 's/clone3/clone3()/'
```
followed by some manual cleanup.

```
execve(["cargo", "clippy"])
execve(["/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/cargo", "clippy"])
execve(["/home/jyn/.local/lib/cargo/bin/cargo-clippy", "clippy"])
execve(["/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/cargo-clippy", "clippy"])
clone3(): Process 6125 attached
[pid  6125] execve(["/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/cargo", "check"])
[pid  6125] clone3(): Process 6126 attached
[pid  6126] execve(["/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/clippy-driver", "/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/rustc", "-vV"])
[pid  6125] clone3(): Process 6127 attached
[pid  6127] execve(["/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/clippy-driver", "/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/rustc", "-", "--crate-name", "___", "--print=file-names", "--crate-type", "bin", "--crate-type", "rlib", "--crate-type", "dylib", "--crate-type", "cdylib", "--crate-type", "staticlib", "--crate-type", "proc-macro", "--check-cfg", "cfg()"])
[pid  6125] clone3(): Process 6129 attached
[pid  6129] execve(["/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/clippy-driver", "/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/rustc", "-", "--crate-name", "___", "--print=file-names", "--crate-type", "bin", "--crate-type", "rlib", "--crate-type", "dylib", "--crate-type", "cdylib", "--crate-type", "staticlib", "--crate-type", "proc-macro", "--print=sysroot", "--print=split-debuginfo", "--print=crate-name", "--print=cfg"])
[pid  6125] clone3(): Process 6131 attached
[pid  6131] execve(["/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/clippy-driver", "/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/rustc", "-vV"])
[pid  6125] clone3(): Process 6134 attached
[pid  6134] execve(["/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/clippy-driver", "/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/rustc", "--crate-name", "example", "--edition=2021", "src/main.rs", "--error-format=json", "--json=diagnostic-rendered-ansi,artifacts,future-incompat", "--crate-type", "bin", "--emit=dep-info,metadata", "-C", "embed-bitcode=no", "-C", "debuginfo=2", "--check-cfg", "cfg(docsrs)", "--check-cfg", "cfg(feature, values())", "-C", "metadata=f3baefdd4f0d88a8", "-C", "extra-filename=-f3baefdd4f0d88a8", "--out-dir", "/home/jyn/.local/lib/cargo/target/debug/deps", "-C", "incremental=/home/jyn/.local/lib/cargo/target/debug/incremental", "-L", "dependency=/home/jyn/.local/lib/cargo/target/debug/deps"])
```

</details>

wow, we have like 10 different commands running here! [^paths]
1. `~/.cargo/bin/cargo clippy -v`
2. `~/.rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/cargo clippy -v`
3. `~/.cargo/bin/cargo-clippy -v`
4. `~/.rustup/.../cargo-clippy clippy -v`
5. `~/.rustup/.../cargo check -v`
6. `~/.rustup/.../clippy-driver ~/.rustup/.../rustc -vV`
7. `~/.rustup/.../clippy-driver ~/.rustup/../rustc - --print=file-names`
8. `~/.rustup/.../clippy-driver ~/.rustup/.../rustc -- --print=sysroot`
9. `~/.rustup/.../clippy-driver ~/.rustup/.../rustc -vV`
10. `~/.rustup/.../clippy-driver ~/.rustup/../rustc src/main.rs`

that's a lot of indirection to compile a simple hello world! let's dig into why it happens.

[^paths]: you may have noticed that i have changed the strace output from `~/.local/lib/rustup` to `~/.rustup`, and likewise for `~/.cargo`. `~/.rustup` and `~/.cargo` are the default locations for rustup and cargo respectively, but i've manually changed them with `CARGO_HOME` and `RUSTUP_HOME` environment variables. the difference doesn't matter for the purpose of this post.

## Rustup toolchain proxies
twice, we see the same binary executed first from `~/.cargo`, then again from `~/.rustup`: for `cargo` and for `cargo-clippy`. what's the difference between these?

let's take a look at the first one that runs:

<details><summary>

```
; strace -e readlink,openat -z ~/.local/lib/cargo/bin/cargo
readlink("/proc/self/exe", "/home/jyn/.local/lib/cargo/bin/cargo", 256) = 36
openat(AT_FDCWD, "/home/jyn/.local/lib/rustup/settings.toml", O_RDONLY|O_CLOEXEC) = 3
```

</summary>

```
; strace -e readlink,openat -s 100 -z ~/.local/lib/cargo/bin/cargo 2>&1| rg 'readlink|openat' | rg -v '"/(lib|etc)'
openat(AT_FDCWD, "/proc/self/maps", O_RDONLY|O_CLOEXEC) = 3
readlink("/proc/self/exe", "/home/jyn/.local/lib/cargo/bin/cargo", 256) = 36
openat(AT_FDCWD, "/home/jyn/.local/lib/rustup/settings.toml", O_RDONLY|O_CLOEXEC) = 3
openat(AT_FDCWD, "/home/jyn/.local/lib/rustup/toolchains", O_RDONLY|O_CLOEXEC) = 3
openat(3, "nightly-x86_64-unknown-linux-gnu", O_RDONLY|O_NOCTTY|O_CLOEXEC) = 4
openat(AT_FDCWD, "/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/lib/rustlib/rust-installer-version", O_RDONLY|O_CLOEXEC) = 3
openat(AT_FDCWD, "/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/lib/rustlib/multirust-channel-manifest.toml", O_RDONLY|O_CLOEXEC) = 3
openat(AT_FDCWD, "/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/lib/rustlib/multirust-config.toml", O_RDONLY|O_CLOEXEC) = 3
readlink("/proc/self/exe", "/home/jyn/.local/lib/rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/cargo", 4096) = 81
openat(AT_FDCWD, "/proc/self/maps", O_RDONLY|O_CLOEXEC) = 3
```

</details>

it turns out this is something called a [rustup proxy]. this is a little shim that [hardlinks] from the proxy (in this case `cargo`) to rustup. rustup then looks at its own executable name, notices that it's cargo, and picks the right toolchain to run the actual cargo binary.

you can actually ask rustup to just find the path without running it:
```
; rustup which cargo
/home/jyn/.rustup/toolchains/nightly-x86_64-unknown-linux-gnu/bin/cargo
```
and you can see that the real cargo doesn't support rustup features like toolchain selectors:
```
; cargo +nightly --version
cargo 1.81.0-nightly (4dcbca118 2024-06-11)
; $(rustup which cargo) +nightly --version
error: no such command: `+nightly`

        Cargo does not handle `+toolchain` directives.
        Did you mean to invoke `cargo` through `rustup` instead?
```
[rustup proxy]: https://rust-lang.github.io/rustup/concepts/proxies.html
[hardlinks]: https://blog.axo.dev/2024/07/an-app-by-any-other-name

you can even verify for yourself that `~/.cargo/bin/cargo` is just a hardlink by comparing the [inodes]:

[inodes]: https://en.wikipedia.org/wiki/Inode
```
; ls -i /home/jyn/.cargo/bin/rustup
32294517 /home/jyn/.cargo/bin/rustup
; ls -i /home/jyn/.cargo/bin/rustc
32294517 /home/jyn/.cargo/bin/rustc
```
## cargo extensions
ok, we've figured out 2/10 of the processes. what are the other 8?

the first one that runs after `cargo clippy` is ... `cargo-clippy` (with a *-* dash, not a space). this is a [cargo extension]; `cargo clippy` is doing very little here other than setting `CARGO` in the environment to point back to itself. why does it need to do that? because `cargo-clippy` is about to call back to cargo: that's process 5, `cargo check`.

what's changed is that [`cargo-clippy` has set][clippy code] [`RUSTC_WORKSPACE_WRAPPER`][workspace wrapper] to `clippy-driver`. that means that instead of invoking rustc on each crate, it's going to invoke clippy-driver. and because it's a `WRAPPER`, it gets passed `rustc` as an argument. the intended use-case is for tools like [`sccache`] that transparently wrap rustc before invoking it; [clippy never invokes rustc as a process][clippy-driver code], but there's no equivalent of [`RUSTC`][rustc var] that's only used for workspaces.

[cargo extension]: https://doc.rust-lang.org/cargo/reference/external-tools.html#custom-subcommands
[clippy code]: https://github.com/rust-lang/rust-clippy/blob/cefa31a5243b90c0c606e2fdb3fc3e036a8bec16/src/main.rs#L109-L126
[workspace wrapper]: https://doc.rust-lang.org/cargo/reference/config.html#buildrustc-workspace-wrapper
[`sccache`]: https://github.com/mozilla/sccache
[rustc var]: https://doc.rust-lang.org/cargo/reference/config.html#buildrustc
[clippy-driver code]: https://github.com/rust-lang/rust-clippy/blob/9cf416dc6ec9d7ebc8df299ca970e4a92efa2596/src/driver.rs#L229-L255
## sysroots and dynamic libraries
now, at this point you might imagine that we have enough info to write our own clippy-like tool that gets invoked instead of rustc. if we look at [`compiler/rustc/src/main.rs` in rust-lang/rust][rustc-main], other than some weird jemalloc stuff, all it does is call `rustc_driver::main`. let's write a toy program that does that.

[rustc-main]: https://github.com/rust-lang/rust/blob/b8bb2968ce1e44d01520c9d59ee6299ed66df3f9/compiler/rustc/src/main.rs

```
; cargo new driver
; cd driver
; echo '#![feature(rustc_private)]
extern crate rustc_driver;
fn main() {
  println!("this is a custom driver!");
  rustc_driver::main();
}' > src/main.rs
; cargo +nightly run
error[E0463]: can't find crate for `rustc_driver`
 --> src/main.rs:2:1
  |
2 | extern crate rustc_driver;
  | ^^^^^^^^^^^^^^^^^^^^^^^^^^ can't find crate
  |
  = help: maybe you need to install the missing components with: `rustup component add rust-src rustc-dev llvm-tools-preview`
```
oh. huh. that's kinda weird. let's follow those instructions for now though.
```
; rustup component add rust-src rustc-dev llvm-tools-preview
; cargo +nightly run
   Compiling driver v0.1.0 (/home/jyn/src/driver)
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 0.08s
     Running `/home/jyn/.cargo/target/debug/driver`
this is a custom driver!
Usage: rustc [OPTIONS] INPUT
...
```
this is pretty cool! let's install it for our user and see if we can run it on a rust file.
```
; cargo install --path . --debug
  Installing driver v0.1.0 (/home/jyn/src/driver)
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 0.01s
  Installing /home/jyn/.cargo/bin/driver
   Installed package `driver v0.1.0 (/home/jyn/src/driver)` (executable `driver`)
; driver src/main.rs
driver: error while loading shared libraries: librustc_driver-5396912e8af1f65d.so: cannot open shared object file: No such file or directory
```
well that's unfortunate. what happened here?

when we use `cargo run` for our driver, it sets some environment variables. in particular, it sets a variable called `LD_LIBRARY_PATH`:
```
; strace -s 1000 -v -e execve cargo run 2>&1 >/dev/null | rg debug/driver | rg -o 'LD_LIBRARY_PATH=[^"]*'
LD_LIBRARY_PATH=/home/jyn/.cargo/target/debug/deps:/home/jyn/.cargo/target/debug:/home/jyn/.rustup/toolchains/nightly-x86_64-unknown-linux-gnu/lib/rustlib/x86_64-unknown-linux-gnu/lib:/home/jyn/.rustup/toolchains/nightly-x86_64-unknown-linux-gnu/lib
```
and indeed if we set that variable, our driver works again:
```
; env "$(strace -s 1000 -v -e execve cargo run 2>&1 >/dev/null | rg debug/driver | rg -o 'LD_LIBRARY_PATH=[^"]*')" driver src/main.rs
this is a custom driver!
```
what does it do?

well, [the linux documentation project][LD_LIBRARY_PATH] documents it as follows:
> You can temporarily substitute a different library for this particular execution. In Linux, the environment variable LD_LIBRARY_PATH is a colon-separated set of directories where libraries should be searched for first, before the standard set of directories; this is useful when debugging a new library or using a nonstandard library for special purposes

[LD_LIBRARY_PATH]: https://tldp.org/HOWTO/Program-Library-HOWTO/shared-libraries.html#AEN80

"library" here means a shared object library. on linux, these files end with `.so`; on Windows, `.dll` (for "dynamic-link library") ; on MacOS, `.dylib`. these are object files that contain code ("symbols" in linker terms) that are loaded by the linker at runtime, after the rest of your program is loaded into memory. for example, if we look at the rustc binary we copied our driver's code from, it's almost empty:
```
;; stat -c %s $(rustup which rustc) | numfmt --to=iec
2.6M
```
instead, almost all the code is in a shared object:
```
; stat -c %s /home/jyn/.rustup/toolchains/nightly-x86_64-unknown-linux-gnu/lib/librustc_driver-5396912e8af1f65d.so | numfmt --to=iec
136M
```
that shared object gets loaded at runtime by every rustc_driver: `rustc`, `clippy`, `rustdoc`, `miri` - and our new `driver` tool. it's actually shipped with every toolchain; if you look at the stable toolchain in `.rustup` you'll see it there too. however, what you *won't* see is the `.rmeta` files in the toolchain directory: [^target-libdir]

```
; ls /home/jyn/.rustup/toolchains/nightly-x86_64-unknown-linux-gnu/lib/rustlib/x86_64-unknown-linux-gnu/lib/*.rmeta | wc -l
228
```

[^target-libdir]: unlike before, these aren't in `nightly/lib`, they're in `nightly/lib/rustlib/x86_64-unknown-linux-gnu/lib`. the difference between these is a little out of scope for this post; basically the former is target-independent and the latter is target-specific. see [what is a sysroot?][sysroot] for more information.

[sysroot]: https://rustc-dev-guide.rust-lang.org/building/bootstrapping/what-bootstrapping-does.html#what-is-a-sysroot

those are the files we just installed with `rustup component add rustc-dev`. they give rustc the type information it needs to check our `driver` code against the type signatures of all the `rustc_private` internal crates.
## building a driver
so! we have learned a bunch of stuff:
1. when we run `cargo` or `rustc`, that's actually running a "rustup proxy" that picks the toolchain version to use
2. when we run `cargo clippy`, that's doing a complicated back and forth between cargo and various clippy executables
3. when we run `rustc` or `clippy-driver`, that's loading a shared object called `librustc_driver.so` at runtime

that's enough info we can now build our own driver!

first, let's make sure we can always run our driver even if we're not going through cargo. it turns out there's a linker flag for this called [`-rpath`][rpath], which does essentially the same thing as `LD_LIBRARY_PATH` but at link time instead of at runtime. to get the path we need, we can use [`rustc --print=sysroot`][print-sysroot] (we can get the right version of `rustc` from an env variable cargo sets, [`RUSTC`]). and finally, to tell cargo to pass that flag to the linker, we can write a build script that uses [`cargo:rustc-link-arg`][cargo-link-arg]. [^rpath-origin]
```rust
// build.rs
fn main() {
    let rustc = std::env::var("RUSTC").unwrap();
    let output = std::process::Command::new(rustc).arg("--print=sysroot").output();
    let stdout = String::from_utf8(output.unwrap().stdout).unwrap();
    let sysroot = stdout.trim_end();
    println!("cargo:rustc-link-arg=-Wl,-rpath={sysroot}/lib")
}
```

[rpath]: https://man.openbsd.org/man1/ld.bfd.1#rpath
[print-sysroot]: https://doc.rust-lang.org/stable/rustc/command-line-arguments.html#--print-print-compiler-information
[cargo-link-arg]: https://doc.rust-lang.org/cargo/reference/build-scripts.html#rustc-link-arg
[`RUSTC`]: https://doc.rust-lang.org/cargo/reference/environment-variables.html#environment-variables-cargo-sets-for-build-scripts

[^rpath-origin]: this strategy embeds an absolute path in our binary. that's fine when running locally, but clippy and other tools want to work on any machine. instead, they use `-Wl,-rpath=$ORIGIN/../lib`, which says "the lib dir relative to the path to the binary that's currently running". we can't use that here because it only works if our binary is in the sysroot, not if it's installed to `~/.cargo/bin`.

next, we want to be able to invoke this as `cargo driver`, so we need to write a `cargo-driver` program.
```rust
// src/bin/cargo-driver.rs
use std::env;
fn main() -> Result<(), i32> {
    let cargo = env::var("CARGO").unwrap_or("cargo".into());
    let mut cmd = std::process::Command::new(cargo);
    let driver = env::current_exe().unwrap().with_file_name("driver");
    let status = cmd.arg("build").env("RUSTC", driver).status().unwrap();
    match status.code() {
        Some(0) => Ok(()),
        Some(other) => Err(other),
        None => Err(-1),
    }
}
```
note that this works fine when using `cargo install`, but when using `cargo run --bin cargo-driver` locally, cargo doesn't rebuild all your binaries automatically - you need to manually run `cargo build` first to make sure both executables are updated. [^bindeps]

[^bindeps]: [artifact dependencies] don't actually help here. they require that the binary you're using come from another crate; they can't be used to specify a target inside the current package.

[artifact dependencies]: https://github.com/rust-lang/cargo/issues/9096
## we get a little silly with it :3

now let's make a program that does something fun! i'm going to disable the `unsafe` checker, so that we can write unsafe programs in "safe" rust.

first, let's tell rust-analyzer that we're using rustc internals in this crate, so we get go-to-definition and other nice things:
```toml
# Cargo.toml
[package.metadata.rust-analyzer]
# This package uses #[feature(rustc_private)]
rustc_private = true
```
we also have to configure this per-editor - i use [helix] so i'm going to configure `.helix/languages.toml`. for vscode you'd use `.vscode/settings.json`.
```toml
# .helix/languages.toml
[language-server.rust-analyzer.config]
rust-analyzer.rustc.source = "discover"
```

[helix]: https://helix-editor.com/

i'm also going to pin a version of the nightly toolchain, since the internal APIs change quite frequently.
```toml
# rust-toolchain.toml
[toolchain]
channel = "nightly-2024-10-20"
components = ["cargo", "llvm-tools", "rust-src", "rust-std", "rustc", "rustc-dev", "rustfmt"]
profile = "minimal"
```
finally, let's write our program.
```rust
// src/main.rs
#![feature(rustc_private)]
extern crate rustc_driver;
extern crate rustc_errors;
extern crate rustc_hir;
extern crate rustc_interface;
extern crate rustc_middle;
extern crate rustc_session;

use rustc_driver::Callbacks;
use rustc_errors::{emitter::HumanReadableErrorType, ColorConfig};
use rustc_interface::interface;
use rustc_session::config::ErrorOutputType;
use rustc_session::EarlyDiagCtxt;

struct DisableSafetyChecks;

impl Callbacks for DisableSafetyChecks {
    fn config(&mut self, config: &mut interface::Config) {
        config.override_queries =
            Some(|_session, queries| queries.check_unsafety = |_tcx, _def_id| {});
    }
}

fn main() {
    rustc_driver::install_ice_hook(
        "https://github.com/jyn514/jyn514.github.io/issues/new",
        |_| (),
    );
    let handler = EarlyDiagCtxt::new(ErrorOutputType::HumanReadable(
        HumanReadableErrorType::Default,
        ColorConfig::Auto,
    ));
    rustc_driver::init_rustc_env_logger(&handler);
    std::process::exit(rustc_driver::catch_with_exit_code(move || {
        let args: Vec<String> = std::env::args().collect();
        rustc_driver::RunCompiler::new(&args, &mut DisableSafetyChecks).run()
    }))
}
```
there's a lot here. i'm not going to cover it all - see [the rustc dev guide][dev-guide] or [generated documentation][nightly-rustc] for that - but i want to call out two things in particular:
1. we had to explicitly use `extern crate` to load the rustc_private crates. normally, cargo will pass (e.g.) `--extern cfg-if=~/.cargo/debug/deps/libcfg-if.rmeta`, so rustc loads our dependencies automatically. these crates are being loaded from the [sysroot], though, so cargo doesn't know about them.
2. the bit of this driver we actually care about is in `queries.check_unsafety = |...| {}`. that says "override the default function that checks unsafety with our own function". rustc is built on a "query" model where information is pulled instead of pushed - see the dev guide section on [queries] for more information.

[dev-guide]: https://rustc-dev-guide.rust-lang.org/
[nightly-rustc]: https://doc.rust-lang.org/nightly/nightly-rustc/
[sysroot]: https://rustc-dev-guide.rust-lang.org/building/bootstrapping/what-bootstrapping-does.html?highlight=sysroot#what-is-a-sysroot
[queries]: https://rustc-dev-guide.rust-lang.org/overview.html#queries

let's test out our driver and make sure it works. we'll install it, create a new project that uses unsafe code without an `unsafe` block, and make sure that project compiles.
```sh
; cd driver
; cargo install --path .
; cd ..
; cargo new safe-rust
; cd safe-rust
; echo 'fn main() { println!("{}", *std::ptr::null::<usize>()); }' > src/main.rs
; cargo driver
   Compiling safe-rust v0.1.0 (/home/jyn/src/safe-rust)
warning: dereferencing a null pointer
 --> src/main.rs:1:28
  |
1 | fn main() { println!("{}", *std::ptr::null::<usize>()); }
  |                            ^^^^^^^^^^^^^^^^^^^^^^^^^^ this code causes undefined behavior when executed
  |
  = note: `#[warn(deref_nullptr)]` on by default

warning: `safe-rust` (bin "safe-rust") generated 1 warning
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 0.06s
```
tada!

---
### a digression: more about rustup proxies
when we built our custom driver, we wrote `driver.rs` and `cargo-driver.rs`, but we never wrote a rustup proxy. how was it picking the right version to run?

well, it was always picking the same version. if we explicitly use a different toolchain - e.g. `stable` - it will still run our custom driver, which uses the nightly toolchain.
```sh
; RUSTFLAGS=--version cargo +stable driver
--- stdout
rustc 1.84.0-nightly (662180b34 2024-10-20)
```
ideally, we would instead have our driver respect the version we're passed. that would also mean stuff like `driver +stable` would give a nice error when that version isn't installed, instead of what it currently does:
```
error: couldn't read +stable: No such file or directory (os error 2)
```
here is a toy shell script that does that, using the same "read my own process name" trick to dispatch to the right binary:
```sh
# ~/.cargo/bin/driver
# ~/.cargo/bin/cargo-driver
#!/bin/sh
case "$1" in
    +*) RUSTUP_TOOLCHAIN=$(echo "$1" | cut -c 1-); shift;;
    *) RUSTUP_TOOLCHAIN=$(rustup default | cut -d ' ' -f 1)
esac
exec rustup run "$RUSTUP_TOOLCHAIN" "$(basename $0)" "$@"
exit 1 # unreachable
```
the other advantage of this shell script is it will let you version your tool. for example, you could install it into `$(rustc --print sysroot)/bin` instead of `~/.cargo/bin`, and have multiple versions depending on what version of rustc it was built with. this is how clippy and the other tools packaged by rustup work, but rustup doesn't support arbitrary drivers, only the hard-coded ones it knows about.
