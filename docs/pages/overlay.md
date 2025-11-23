---
title: overlay filesystem
---
◊(def dirp ".build/defaults")
◊(def dirm (str \` dirp \`))
◊(def f "`flower`")

## VFS

◊f supports a "poor man's [overlay filesystem]", which it calls a "[virtual filesystem]" or "VFS".
For example, if you have both a file named `◊dirp/build.clj` and a file named `build.clj`, ◊f will use your `build.clj`.
But if you don't have your own `build.clj`, it will fallback to `◊dirp/build.clj`.

This ◊dirm directory is generated during `flower new`.
See [architecture](./architecture.md) for more detailed information.

## virtual and materialized files

Some defaults are never hidden in ◊dirm.
For example, `flower.edn` is always placed into your top-level directory so that ◊f can recognize it as a flower site.
Such defaults are called "materialized files", because they really do exist on disk, and are overridden just by editing them in place.
By contrast, defaults that are not materialized are called "virtual files".

To avoid ◊f generating "hidden" pages, it will always materialize the `pages/` and `static/` directories.
Currently, ◊f always materializes the `templates/` directory, but that may change in the future.

## overriding defaults

Right now, the set of operations you can do with the virtual defaults are quite limited:
basically the only thing you can do is override them.
You may find it useful to look at the default file you are overriding to get ideas; you can do so by looking in the `.build/defaults` directory that `flower new` generates.

## future plans

I plan to add a `flower edit` command in the future that makes it simpler to override defaults.
I also plan to add `flower upgrade` to make it possible to upgrade to a newer version of flower's defaults.
I plan to move ◊dirm to `defaults/` so that it's tracked by git.
Finally, I plan to add support for "presets" (often called "themes" in other SSGs) that use the same VFS mechanismm.
See [#74](https://codeberg.org/jyn514/flower/issues/74) for more info.

[overlay filesystem]: https://docs.kernel.org/filesystems/overlayfs.html#upper-and-lower
[virtual filesystem]: https://www.kernel.org/doc/html/next/filesystems/vfs.html
