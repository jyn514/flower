(set! *warn-on-reflection* true)
(ns flower.main
  (:gen-class)
  (:use flower.internal.utils)
(:require
 [babashka.cli :as cli]
 ; https://clojurians.slack.com/archives/CLX41ASCS/p1753986315453519
 [babashka.process.pprint]
 [clojure.data.json :as json]
 [clojure.string :as str]
 [flower.beholder]
 [flower.cmd :as cmd]
 [flower.defaults]
 [flower.frontmatter :refer [split-frontmatter]]
 [flower.hiccup]
 [flower.internal.utils]
 [flower.reflect]
 [flower.repl :as repl]
 [flower.utils]
 [flower.watch]
 [hiccup.util]))

(def VERSION "0.0.1")

; CLI and IO

(defn read-json []
        ; TODO: https://clojure.atlassian.net/browse/DJSON-43
  (let [reader (java.io.PushbackReader. *in* 64)]
    (try (json/read reader :key-fn keyword)
         ; bruh what is up with the json parser not having scoped exceptions
         (catch java.lang.Exception e
           (fatal "failed to parse JSON:" (ex-message e))))))

(defn map-json
  "Given a function `f` that transforms a clojure map to a clojure map,
   read the map as JSON from stdin and write it to stdout.
   If any `args` are present, they will be passed after the map."
  [f & args]
  (let [before (read-json)
        after (cmd/with-tracked-deps (:dependencies before) #(apply f before args))]
    (json/write after *out*)))

(defn no-opts [f & args]
  (fn [& _] (apply f args)))

(defn unknown-command [{:keys [args]}]
  (fatal (str "unrecognized command: '"
              (str/join " " args)
              "' (-h for help, or 'watch' to build your site)")))

(declare dispatch-table)

(defn ->help-1
  "Convert our `dispatch-table` DSL to babashka/format-opts syntax.
  format-opts expects the following input:
  `{:spec [[:option {:parse-opts-opt :val}]]}`"
  [[k v]]
  [(keyword k) (if (map? v) v {})])

(defn ->help
  []
  {:spec (->> (for [[ks v] dispatch-table]
                (if (sequential? ks)
                  (for [k ks] (->help-1 [k v]))
                  [(->help-1 [ks v])]))
              (apply concat))})

(defn help []
  (-> (->help) cli/format-opts println))

(def dispatch-table
  {"configure" (no-opts cmd/configure {})
   "split-frontmatter" (no-opts map-json split-frontmatter)
   "render-page" (no-opts map-json cmd/render-page {})
   "render-index" (no-opts map-json cmd/render-index)
   "render-markdown" (no-opts map-json cmd/render-markdown)
   "embed-template" {:fn #( map-json cmd/embed-template %)
                     :coerce {:template-name :string}
                     :args->opts [:template-name]}
   "transform" {:fn #( map-json cmd/transform %)
                     :coerce {:transformer :string}
                     :args->opts [:transformer]}
   "split-dependencies" {:fn #(map-json cmd/split-dependencies %)
                         :coerce {:depfile :string :out-file :string}
                         :args->opts [:depfile :out-file]}
   "split-sass-dependencies"
     {:fn #(-> (read-json) (cmd/split-sass-dependencies %) println)
      :coerce {:source-file :string}
      :args->opts [:source-file]}
   "watch" {:fn flower.watch/watch
            :coerce {:port :number}}
   "repl" {:fn flower.repl/repl
           :coerce {:template :boolean}
           :args->opts [:template]}
   "new" (no-opts flower.defaults/materialize-all)
   ; TODO: this overrides --data
   "jq" {:fn #(println (cmd/jq (assoc % :data (slurp *in*))))
         :coerce {:raw-input :boolean
                  :raw-output :boolean
                  :data :string
                  :query :string}
         :aliases {:R :raw-input :r :raw-output}
         :args->opts [:query]}
   ["version" "--version"] (no-opts println VERSION)
   ["help" "--help" "-h" "/?"] (no-opts help)
   [] {:fn unknown-command :needs-metadata true}})

(defn ->bb
  "Convert our `dispatch-table` DSL to babashka/dispatch syntax.

  `init` is a function that will run before the dispatched command
  to set up global options. It takes two arguments:
  the function to run inside globals and the parsed options.
  It should pass the options as an argument to the function."
  [init key val]
  (if (and (vector? key) (seq key))
    (for [cmd key] (->bb init cmd val))
    (let [cmds (if (string? key) [key] key)
          [my-fn opts] (if (map? val) [(:fn val) val] [val {}])
          wrapped-fn (if (:needs-metadata opts) my-fn #(my-fn (:opts %)))
          bb-map (assoc opts :cmds cmds :fn #(init wrapped-fn %))]
      bb-map)))

(defn init-fn [cmd-fn args]
  (binding [*site* (or (get-in args [:opts :C]) ".")
            flower.reflect/*watching* (boolean (or (= "watch" (:dispatch args))
                                                   (env "FLOWER_WATCH")))]
    (cmd-fn args)))

(defn dispatch-cmd
  "Parse the CLI args and dispatch to the appropriate clojure funciton.
  Also registers global options."
  [args]
  (let [table (map #(apply ->bb init-fn %) dispatch-table)
        flat-table (flatten table)]
    (cli/dispatch flat-table args {:coerce {:C :string}})))

(defn main [& args]
  (try
    (dispatch-cmd args)
    0
    (catch java.lang.Exception e
      (binding [*out* *err*]
        (repl/print-trace e false))
      1)
    (finally
      (shutdown-agents)
      (flush))))

(defn -main [& args]
  (System/exit (apply main args)))
