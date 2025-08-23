; https://clojure.org/guides/dev_startup_time
(binding [*compile-files* true
          *compile-path* "target/repl-cache"]
  (require '(clojure [string :as str]))
  (use 'clojure.repl 'clojure.repl.deps 'flower.internal.utils)
  (import org.jsoup.Jsoup org.jsoup.parser.Parser)
  (require '(clojure [string :as str])
          '(clojure.data [json :as json])
          '[clojure.reflect :as r]
          '[clojure.stacktrace :refer [print-stack-trace]]
          '(flower [main :as flower])
          '[flower.eval :as eval]
          '(sci [core :as sci])
          '(instaparse [core :as insta])
          '(jq [api :as jq])
          '[clj-yaml.core     :as yaml]
          '[toml-clj.core :as toml]
          '[java-time.api :as jt]
          '[nextjournal.beholder :as behold]
          '[hiccup2.core :refer [html]]
          '(babashka [process :as ps])
          '(babashka [cli :as cli])
          '(babashka [fs :as fs])
          '[expectations.clojure.test :as expect :refer [defexpect]]
          '[clojure.test.check.generators :as gen]))

(defn members [val]
  (->> val r/reflect :members (map :name) set sort))

(defn feval [form]
  (eval/eval-form (eval/create-sci-cx {}) form))

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
