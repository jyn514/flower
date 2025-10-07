## background

"flower core" (the flower binary itself) is intentionally minimal.
as much work as possible is pushed out to the runtime interpreted clojure.

## flower core

Flower core has the following model:
1. On `flower new`:
    - if `flower.edn` doesn't exist yet, create it.
    - run `flower configure`
2. On `flower configure`:
    - if `flower.edn` doesn't exist yet, error.
    - populate a `.build/defaults` directory with all built-in default files. see [overlay](./overlay.md) for details.
    - parse global configuration from `flower.edn`
    - parse per-page configuration from `pages/*`
    - run `build.clj`, passing the configuration in `flower.reflect/*metadata*`.
      `build.clj` is expected to call `flower.reflect/write-ninja!`, and the output sent there is used directly as `build.ninja`; there is no post-processing.
      finally, record the dependencies used by `build.clj` in a `depfile`. `build.clj` is expected to add a dependency edge between the depfile and build.ninja itself.
3. On `flower build`:
    - run `flower configure`
    - run `ninja`
4. On `flower watch`:
    - run `flower configure`. exit if it errors.
    - run `ninja`. do not exit if it errors.
    - start a web server on the out directory (usually `public`).
    - start a live-reload server using web sockets (WSS) on port 35729.
    - start a file watcher on the out directory.
        - on changes to the out directory, send a live-reload message over the WSS server.
    - start a file watcher on the site directory (usually the same as the current directory)
        - on changes anywhere in the site, rerun `ninja`

There are a few other non-interactive ("plumbing") commands, described in the section on `default-build.clj` below.

Note some interesting properties of this model:
- changes to the out directory do not have to come from flower itself. any change to `public` will cause a live-reload message to be sent.
- there is a process boundary between `flower` and `ninja`. most builds of any site occur in a `flower` subprocess, not in the flower watcher.
  this allows modifying flower and see the results live, without having to kill and restart the watcher.
  the downside is that all data must be serialized to the filesystem, either through `build.ninja` or through a temporary file in `.build`.
- all builds happen in parallel, automatically. rebuild detection happens automatically (based on file timestamps).

## default-build.clj

The default build plan looks something like this per-page:
1. Run `flower split-frontmatter pages/page.md`, which generates a structured JSON file in `.build`. This JSON contains both the frontmatter and the content itself.
2. Once all frontmatter has been split, run `flower join-frontmatter` to combine them in one giant file. This contains only frontmatter, no content. Having a cache like this allows an "early stop" condition if only the page's content has changed, not its frontmatter.
3. If necessary, rerun `build.clj` with the new frontmatter.
4. For each file, run `flower transform` on all transformers. Save the output to `public`.

## transformers

Flower's main two interfaces are `build.clj` (through `flower configure`) and `flower transform`.
Transformers are clojure files which receive clojure data as input and emit clojure data as output.
Within a transformer, you are allowed to do basically anything allowed by the sandbox.

The initial transformer is passed a `{:content :string, :frontmatter {}}` map.
Past that, transformers are allowed to change their inputs and outputs as they please.

The default transformers run in the following order:
1. `preprocess`: Expand the template language (see [sunflower](language.md)).
2. `markdown`: Render markdown files to HTML. Flower tracks the current filetype using `:flower/filetype`. The initial value is determined by the source file name.
3. `embed`: Embed the page inside of a template. The default template is `templates/default.html`. Use `template: null` to explicitly say that no template should be used.
4. "all user-defined transformers" (order unspecified, configurable with a custom `expressions/transform-sorter.clj`)
5. `content`: Extract the `content` field from the Clojure map and save it to disk as the final page.
