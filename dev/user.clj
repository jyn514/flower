; https://clojure.org/guides/dev_startup_time
(binding [*compile-files* true
          *compile-path* "target/repl-cache"]
  (require '(clojure [string :as str]))
  (use 'clojure.repl 'clojure.repl.deps 'flower.internal.utils 'flower.select)
  (import org.jsoup.Jsoup org.jsoup.parser.Parser)
  (require '(clojure [string :as str])
          '(clojure.data [json :as json])
          '[clojure.reflect :as r]
          '(flower [main :as flower])
          '(sci [core :as sci])
          '(instaparse [core :as insta])
          '(jq [api :as jq])
          '[nextjournal.beholder :as behold]
          '(babashka [process :as ps])
          '(babashka [cli :as cli])
          '(babashka [fs :as fs])))

(defn members [val]
  (->> val r/reflect :members (map :name) set sort))

; (defmacro trace [& args]
;   `(do (add-lib 'org.clojure/tools.trace)
;        (ns-unmap *ns* '~'trace)
;        (use 'clojure.tools.trace)
;        (clojure.tools.trace/trace ~@args)))
(defmacro add-trace []
  (add-lib 'org.clojure/tools.trace)
  (ns-unmap *ns* 'trace)
  (use 'clojure.tools.trace))
       ; (clojure.tools.trace/trace ~@args)))
