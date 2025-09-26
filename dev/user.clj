; https://clojure.org/guides/dev_startup_time
(binding [*compile-files* true
          *compile-path* "target/repl-cache"]
  (require '(clojure [string :as str]))
  (use 'clojure.repl 'clojure.repl.deps 'flower.utils)
  (import org.jsoup.Jsoup org.jsoup.parser.Parser)
  (require '(clojure [string :as str])
          '(clojure.data [json :as json])
          '[clojure.edn :as edn]
          '[clojure.set :as set :refer [union]]
          '[clojure.reflect :as r]
          '[clojure.walk :as walk :refer [walk postwalk prewalk]]
          '[clojure.java.io :as io]
          '[clj-commons.ansi :as ansi]
          '[malli.core :as m]
          '[malli.dev]
          '(flower [main :as flower])
          '[flower.eval :as eval]
          '[flower.cmd :as cmd]
          '[flower.stacktrace]
          '(sci [core :as sci])
          '(instaparse [core :as insta])
          '[clj-yaml.core     :as yaml]
          '[toml-clj.core :as toml]
          '[nextjournal.markdown :as md]
          '[java-time.api :as jt]
          '[hiccup2.core :refer [html]]
          '(babashka [process :as ps])
          '(babashka [cli :as cli])
          '(babashka [fs :as fs])
          '[flower.http-server :as http-server]
          #_'[expectations.clojure.test :as expect :refer [defexpect]]
          #_'[clojure.test.check.generators :as gen]))

(defn members [val]
  (->> val r/reflect :members (map :name) set sort))

(def eval eval/eval-form)

(defn transform [src]
  (eval/transformer (eval/parse src) src (eval/create-sci-cx "<repl>")))

(defn render [src]
  (eval/preprocess-file src "<repl>"))

(defn spans [src]
  (postwalk #(do (print % ": ") (some-> % meta println) %) src))

(defn with-err-handler [f & args]
  (try (apply f args) (catch clojure.lang.ExceptionInfo e (flower.stacktrace/print-cause-trace e))))

(def cx (eval/create-sci-cx "<repl>"))
(alter-var-root #'eval/*cx* (constantly cx))
(alter-var-root #'flower.unsafe/*drop-bomb* (constantly false))

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

(malli.dev/start!)
