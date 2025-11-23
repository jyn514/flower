---
preprocessors: []
---
## page templates

Templates in `flower` work similar to other SSGs.
A template is a file in the `templates/` directory.
Any page in your site can be embedded into a template.

`flower` determines which template to use in the following way:
1. if your page has a `template: xxx` key, `templates/xxx` is used.
2. if your page has no `template` key, `templates/default.html` is used.
3. if your page has a `template: null` key, no template at all is used and the page is emitted as-is.

This logic can be overridden by creating a custom `transformers/embed.clj` file.
See [overriding defaults](./overlay.md#overriding-defaults) for more info.

## creating new templates

Templates are almost exactly the same as pages, except that they have an injected `content` variable.
`content` holds your page, rendered as HTML.
To change whether your page is rendered before embedding (e.g. if you have a `.md` markdown template), override the default `expressions/transform_sorter.clj` so that `embed` comes before `markdown`.
To change the name of the injected variable, override the default `transformers/embed.clj`.

## embedding

`flower` ships with an `expressions.meta/embed` function in the defaults.
This can be used to embed templates in other templates.
For example, this will call the `skeleton.html` template with an injected `content` variable:
```clj
◊(def body)«
  <p>this is my page!</p>
»
◊(embed "skeleton.html" {'content body})
```
Notice that this still uses `◊` sunflower syntax. That's because you can write normal HTML before or after the `embed` call if you need more flexibility.
