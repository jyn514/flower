; Portions copyright Masashi Iizuka under Eclipse Public License 2.0
; see https://github.com/liquidz/frontmatter
; (set! *warn-on-reflection* true)

(ns flower.core
  (:gen-class)
  (:use flower.internal.utils)
(:require
  [babashka.process.pprint] ; https://clojurians.slack.com/archives/CLX41ASCS/p1753986315453519
  [babashka.cli :as cli]
  [hiccup.util]
  [clojure.string :as str]
  [clojure.data.json :as json]
  [flower.build :as build]
  [flower.cmd :as cmd]
  [flower.eval]
  [flower.select]
  [flower.hiccup]
  [flower.reflect]
  [flower.defaults]
  [flower.live-reload]
  [flower.utils]
  [flower.internal.utils]))

(def VERSION "0.0.1")

; CLI and IO

(defn map-json
  "Given a function `f` that transforms a clojure map to a clojure map,
   read the map as JSON from stdin and write it to stdout.
   If any `args` are present, they will be passed after the map."
  [f & args]
  ; TODO: https://clojure.atlassian.net/browse/DJSON-43
  (let [reader (java.io.PushbackReader. *in* 64)
        before (try (json/read reader :key-fn keyword)
                    ; bruh what is up with the json parser not having scoped exceptions
                    (catch java.lang.Exception e
                      (fatal "failed to parse JSON:" (ex-message e))))
        after (apply f before args)]
    (json/write after *out*)))

(defn help []
  (fatal "help is not yet implemented, sorry"))

(defn no-args [f]
  (fn [& _] (f)))

(defn unknown-command [{:keys [args] :as m}]
  (fatal (str "unrecognized command: '"
              (str/join " " args)
              "' (-h for help, or 'watch' to build your site)")))

(def dispatch-table
  {"configure" (no-args cmd/configure)
   "split-frontmatter" (no-args #(map-json build/split-frontmatter))
   "render-page" (no-args #(map-json cmd/render-page {}))
   "render-index" (no-args #( map-json cmd/render-index ))
   "embed-template" {:fn #( map-json cmd/embed-template %)
                     :coerce {:template-name :string}
                     :args->opts [:template-name]}
   "transform" {:fn #( map-json cmd/transform %)
                     :coerce {:transformer :string}
                     :args->opts [:transformer]}
   "split-dependencies" {:fn #(map-json cmd/split-dependencies %)
                         :coerce {:depfile :string :out-file :string}
                         :args->opts [:depfile :out-file]}
   "watch" flower.live-reload/watch
   "new" (no-args flower.defaults/materialize-all)
   ; TODO: this overrides --data
   "jq" {:fn #(println (cmd/jq (assoc % :data (slurp *in*))))
         :coerce {:raw-input :boolean :raw-output :boolean
                  :data :string :query :string}
         :aliases {:R :raw-input :r :raw-output}
         :args->opts [:query]}
   ["version" "--version"] (no-args #(println VERSION))
   ["help" "--help" "-h"] (no-args help)
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
          wrapped-fn (if (:needs-metadata opts) my-fn #(my-fn (:opts %)))]
      (assoc opts :cmds cmds :fn #(init wrapped-fn %)))))

(defn dispatch-cmd
  "Parse the CLI args and dispatch to the appropriate clojure funciton.
  Also registers global options."
  [args]
  (let [init #(binding [*site* (or (get-in %2 [:opts :C]) ".")]
                (%1 %2))
        table (map #(apply ->bb init %) dispatch-table)]
    (cli/dispatch table args {:coerce {:C :string}})))

(defn -main [& args]
  (try
    (dispatch-cmd args)
    (catch clojure.lang.ExceptionInfo e
      (if (and (-> e ex-data :flower/exit)
               (not (System/getenv "FLOWER_HOST_TRACE")))
        (do
          (eprintln (ex-message e))
          (System/exit 1))
        (throw e)))
    (finally
      (shutdown-agents)
      (flush))))
