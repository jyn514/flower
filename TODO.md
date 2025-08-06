# TODO

- rename transformer->transform
- livereload broken ????
- hiding `def` vars isn't working ???
- .gitignore in defaults/
- clojure.repl doesn't get loaded into SCI
    - add `flower repl`?
- template embedding
- template defaults
- RSS feed
- syntax highlighting
- Sass compiler
- add the rest of the HTML selector apis lol
- allow configuring build dirs in `flower.toml`
- sandbox fs APIs for build.clj
- file watcher should give a nice error if dir doesn't exist
- nice syntax over ninja phony targets so you have some equivalent of `--drafts`
  - build/generate lets you pass `:task "drafts"` for each `:build`, defaults to `"default"`
- flower emojis
- `index = true` doesn't work in pages
- always run configure to avoid bootstrapping problems
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

- SVG with embedded text for code blocks
  - https://wheybags.com/blog/macroblog.html#better_code_snippets
