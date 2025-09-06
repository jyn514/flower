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

