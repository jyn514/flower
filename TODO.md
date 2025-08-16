# TODO

- `◊span[{:class "author"}]{jyn}`
- `◊tag['span {:class "author"}]{jyn}`

- test in CI that `scripts/clean-build.sh` works

- (compile) so startup time is better
- don't materialize onto disk by default
    - .build/vfs (maybe with chattr -i)
    - reload vfs itself when flower changes using ninja
    - custom merge isn't *that* hard https://github.com/mystor/git-revise/blob/main/gitrevise/merge.py
- write lots of tests
    - pprint
- document how to type lozenge
    - ◊ is option+shift+v on macOS
    - ⋄ is compose+<> on linux
    - point people to https://github.com/samhocevar/wincompose on windows
    - document how to set this up in common editors (vim, vscode, helix, zed)

- prelude module? maybe inject `(eval "expressions/prelude.clj")`?
- `flower help` is broken lol lmao
- fix spans for SCI eval
- clojure.repl doesn't get loaded into SCI
    - add `flower repl`?
- template defaults
- add the rest of the HTML selector apis lol
- allow configuring build dirs in `flower.toml`
- sandbox fs APIs for build.clj
- file watcher should debounce nvim delete/recreate
  - https://github.com/clojure/core.async
- file watcher should cancel existing ninja processes if a new input is changed
- generated build.ninja can't handle file deletes
  - probably i can avoid this by deleting all the outputs of `ninja -t query <deleted file>`
- `watch` tries to rebuild if an intermediate file was rebuilt.
  - get a list of all outputs of the modified file and ignore them if they happen within a ~second of a completed build? unsure
  - debouncing will help probably
- nice syntax over ninja phony targets so you have some equivalent of `--drafts`
  - build/generate lets you pass `:task "drafts"` for each `:build`, defaults to `"default"`
  - note that `ninja -t browse` defaults to `all`. hm.
- flower emojis
- `index = true` doesn't work in pages
- templates can end with .md
- render-page for each template
- should not lose `page` with `embed-template`
- maybe `◊include` should be relative to `includes` instead of `templates`?
- allow more things to be dynamic with depfiles?
  - e.g. `templates` could be fed through with JSON
  - `embed-template` could be a post-processor
- bundle asciidoc; suggest for highly nested md->clj->md->clj
- allow preprocessors to edit frontmatter
- render needs to not blindly assume that the preprocessing language is clojure
- `embed` is the fixed point algorithm, calls render
- dependencies between index pages and rendered pages
  - if we want "leads", they need to depend on contents also
  - if we want titles, we need them to be frontmatter

  <!-- - maybe `transformers/index.clj`, present by default, returns list of strings -->
  <!-- - files are recognized by  -->

## ideating

- `<a>` is a valid clojure ident???
- have a 7 line outline for `defaults/build.clj`
- hard-code `base` in the binary, since people can override it
- define *an* order for transforms
  - defined in build.clj, defaults to alphabetical
  - uses normal file extensions to determine how to run the transformer
  - default function runs embed first
  - very very simple to modify order, just a static list
  - warn if a transformer is missing from the list

- preprocessors *are* different from templates and transforms because they have a fixed point algorithm with templates
- preprocessors are clojure files with a unified interface (`def transform`)
  - add `flower.unsafe/process`, asks you what the deps are
- `render` needs a mapping between language and preprocessor
  - can generate a JSON file in .ninja or something

"magic should only be for things you almost certainly want"

<!-- don't think this works
- turn `render-page` into a transformer you can look at
  - add `flower.unsafe/process`, asks you what the deps
  - "transformers"
  - you can add a custom transformer that calls lua via `unsafe/process`
- allow build.clj to do file reads; reconfigure on any change
  - `unsafe/process` is fine as long as it doesn't write
  - generating custom input pages is hard because it's expensive but we also can't move it to a later phase

1. `flower configure`
2. `ninja` runs all transforms in order defined by build.clj

default order:
- preprocessing
- embedding
- custom transforms

ninja's api for all of these is `flower transform`
-->

- ~~build.clj can be much simpler now that there's a unified api~~
- ~~"build.ninja is mostly for custom commands"~~

## goodies

- RSS feed
- syntax highlighting
  - https://tree-sitter.github.io/tree-sitter/3-syntax-highlighting.html
  - https://github.com/bonede/tree-sitter-ng
  - https://github.com/seart-group/java-tree-sitter
- Sass compiler
- bsky comments
  - https://natalie.sh/posts/bluesky-comments/
- SVG with embedded text for code blocks
  - https://wheybags.com/blog/macroblog.html#better_code_snippets
- blog pingback protocol
