◊(use 'expressions.constants)

## setup

### [install ninja](https://github.com/ninja-build/ninja/wiki/Pre-built-Ninja-packages)

### install the flower binary

Two options:
#### download a static binary
1. go to https://github.com/jyn514/flower/actions?query=event%3Apush+branch%3Adev+is%3Asuccess
2. click on the latest successful action
3. scroll down to "Artifacts"

#### build from source

See [contributing.md](./contributing.md).

## create your site

1. `mkdir my-site`
2. `cd my-site`
3. `flower new`
4. `flower watch`

`flower new` generates the skeleton of a site in the current directory.
Feel free to edit any files it generates.

## write a post

Add your posts in `pages`.
Flower infers the filetype from the file extension.
Currently, only `.md` (markdown) and `.html` (HTML) are supported.
Pages can use the [sunflower template language](./language.md) to embed clojure.

## modify the defaults

Most of flower's defaults can be used as-is without changes.
One default you will want to modify, however, is `expressions/constants.clj`.
This contains various constant values that are used in various places through the site.
Here are the constants for the flower docs themselves, which you can use as an example:
```clj
(def site-title ◊(pr-str site-title))
(def site-author ◊(pr-str site-author))
(def global-desc ◊(pr-str site-title))
(def lang ◊(pr-str lang))
```

You likely will also want to change the HTML templates for your pages.
The default template is `templates/default.html`.

For more information, see [the guide](./guide.md).
