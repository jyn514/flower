## sunflower

The flower template language is a very thin wrapper around a clojure interpreter.
The working title of the language is "sunflower".

## clojure

To use sunflower, you will need to know some amount of clojure. Here are some resources you can use:
- [Learn X in Y minutes Where X=Clojure](https://learnxinyminutes.com/clojure/)
- The official ["Learn Clojure"](https://clojure.org/guides/learn/clojure) guide.
- [Clojure for the Brave and True](https://www.braveclojure.com/clojure-for-the-brave-and-true/)
- [Clojure cheatsheet](https://clojure.org/api/cheatsheet)
- [Clojure Quickref](https://clojuredocs.org/quickref)
- [Clojure standard library reference docs](https://clojuredocs.org/core-library)

## language intro

the escape character is `◊`. `◊(func args)` calls a function and emits the return value into the template.

```
Here is a flower site. You can do arithmetic in a template! 2 + 2 is ◊(+ 2 2).
```

`◊x` emits the  variable `x` into the template.

```
◊(def x 2)
I have written ◊x flower sites.
```

for loops and conditionals are done with the clojure standard library.

```
◊(def titles
   (for [page pages]
     (if (:name page) name "unknown page")))
```

`◊(func args)«body»` allows nesting markup inside a function call. You can repeat `«» «»` to pass multiple arguments. Space before an opening `«` is allowed. You can use `◊` inside nested markup.

```
jyn says that ◊(if name)«
  her name is ◊name.
»
«
  she forgot her name.
»
```

`◊;` comments the rest of the line. `◊#_(...)` is a structural comment. `◊«`, `◊»`, and `◊◊` escape their special characters, respectively.
```
◊; this is a comment that can have weird characters: !@#$4^*#()%$1}[];:,./
◊#_(this code must be valid clojure syntax, since it is parsed by the clojure reader
    otherwise, none of the identitifiers need to resolve)
```

template embedding and includes are done with clojure function calls.
```
◊(include "header.html")
◊(embed "page.html" {'body body})
```

let's put that all together into some real code that might be used in a flower template:
```html
◊(require '[expressions.meta :refer [embed]])
◊(def body)«
 <div class="trigger">
    ◊(for [section subsections
          :when (:title section)])«
      <a class="page-link" href="◊(:path section)">◊(:title section)</a>
    »
    ◊; TODO: implement `a` for anchors
    ◊#_(a {:class "page-link" :href "/computer-of-the-future"}){the computer of the next 200 years}
 </div>
 »
◊(embed "page.html" {'body body})
```

## sandboxing

flower runs clojure code in a sandbox. most access to the filesystem is disallowed. writing files is banned altogether.

embedded clojure can write to stderr like normal, but it cannot write to stdout, because that's used by flower for internal communication.
attempts to do so will be redirected to stderr.
a side-effect of this is that `println` appears directly in the terminal instead of ending up embedded in the page.

## filesystem API

to get a list of metadata for just the current page, use the `page` binding injected into your local scope.
to get a list of all page metadata, use the `pages` binding.
to read files, use `flower.reflect/read-file`, which does dependency tracking to make sure your page gets rebuilt if the file you read changes.

## libraries

Flower lets you use many clojure and Java libraries that are bundled with the executable.

TODO: exhaustive list. for now see `flower.eval/sci-defaults` under `:namespaces`.

## `flower.unsafe`

if you need to do something that isn't allowed by the sandbox, use `flower.unsafe`.
currently the only public API is `flower.unsafe/system`, which allows you to spawn processes,
and `flower.unsafe/register-dependencies!`, which tells flower when your clojure file needs to be rebuilt.
