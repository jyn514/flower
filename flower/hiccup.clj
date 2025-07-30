; modified from https://github.com/babashka/babashka/blob/bd20a5f973fcf1ed33131eb1079927be9de0d1d8/feature-hiccup/babashka/impl/hiccup.clj
; avoids the use of `eval` in hiccup/html, which isn't supported by Graal
; see https://github.com/oracle/graal/issues/11327

(ns flower.hiccup
  "internals, do not use"
  (:require [hiccup.compiler :as compiler]
            [hiccup.util :as util]))

(defmacro html-2
  "Render Clojure data structures to a compiled representation of HTML. To turn
  the representation into a string, use clojure.core/str. Strings inside the
  macro are automatically HTML-escaped. To insert a string without it being
  escaped, use the [[raw]] function.
  A literal option map may be specified as the first argument. It accepts two
  keys that control how the HTML is outputted:
  `:mode`
  : One of `:html`, `:xhtml`, `:xml` or `:sgml` (defaults to `:xhtml`).
    Controls how tags are rendered.
  `:escape-strings?`
  : True if strings should be escaped (defaults to true)."
  {:added "2.0"}
  [options & content]
  (if (map? options)
    (let [mode            (:mode options :xhtml)
          escape-strings? (:escape-strings? options true)]
      `(binding
           [util/*html-mode* ~mode
            util/*escape-strings?* ~escape-strings?]
         (util/raw-string (compiler/render-html (list ~@content)))))
    `(util/raw-string (compiler/render-html (list ~@(cons options content))))))
