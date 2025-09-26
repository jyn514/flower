# TODO

- `◊span[{:class "author"}]{jyn}`
- `◊tag['span {:class "author"}]{jyn}`

- want to implement preprocessors before writing the docs site so i don't need double-escaping
- test all subcommands exhaustively, assert that i have tested them all

- don't bind locals for transformers, only for pages (allows `ns`)
- turn render-page into a transformer
  - calls `flower/render-file` by default
  - can use `flower.unsafe/process` for other langs
    - works as a "runner", can use the same transformer for many different transformers
  - gives us fewer caching points but that's fine, don't do dumb things that make builds slow
- https://github.com/noprompt/garden for CSS generation
- reload vfs itself when flower changes using ninja
- custom merge isn't *that* hard https://github.com/mystor/git-revise/blob/main/gitrevise/merge.py
- write lots of tests
    - test in CI that `scripts/clean-build.sh` works
    - pprint
    - repl works without warnings
    - can build jyn.dev
    - incremental builds do nothing if no file has changed
    - incremental builds rebuild if necessary
- auto-escaping with `hiccup.util/escape-html`
  - opt-out with `hiccup/raw`
  - only support HTML and attribute contexts
  - urls: java.net.URLEncoder
  - css: port https://github.com/mathiasbynens/CSS.escape/blob/master/css.escape.js
  - js: https://commons.apache.org/proper/commons-lang/javadocs/api-2.6/org/apache/commons/lang/StringEscapeUtils.html#escapeJavaScript(java.lang.String)
- hiccup syntax is ok for embedded clojure, but it would be nice to have a simpler standalone syntax based on css selectors
  - https://haml.info/tutorial.html
  - https://web.archive.org/web/20190630223046/http://jade-lang.com/
  - https://code.google.com/archive/p/zen-coding/
  - https://maud.lambda.xyz/

- prelude module? maybe inject `(eval "expressions/prelude.clj")`?
- `transformers/redirect.clj`
  - allows relative paths with /foo.html
- `flower help` is broken lol lmao
- fix spans for SCI eval
- template defaults
- add the rest of the HTML selector apis lol
- allow configuring build dirs in `flower.edn`
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
- should not lose `page` with `embed-template`
- maybe `◊include` should be relative to `includes` instead of `templates`?
  - nah
- bundle [asciidoc]; suggest for highly nested md->clj->md->clj
- allow preprocessors to edit frontmatter
- render needs to not blindly assume that the preprocessing language is clojure
- bundle jinja by default
- dependencies between index pages and rendered pages
  - if we want "leads", they need to depend on contents also
    - actually wait depfiles solve this owo
  - if we want titles, we need them to be frontmatter

  <!-- - maybe `transformers/index.clj`, present by default, returns list of strings -->
  <!-- - files are recognized by  -->

[asciidoc]: https://codeberg.org/jyn514/flower/issues/37

## ideating

- have a 7 line outline for `defaults/build.clj`
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
  - someone mentioned on slack they got this working: https://clojurians.slack.com/archives/C03S1KBA2/p1756400041864609?thread_ts=1756162447.266729&cid=C03S1KBA2
- bsky comments
  - https://natalie.sh/posts/bluesky-comments/
- SVG with embedded text for code blocks
  - https://wheybags.com/blog/macroblog.html#better_code_snippets
- blog pingback protocol
  - oh this isn't actually real lol
