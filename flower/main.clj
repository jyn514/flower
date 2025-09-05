(set! *warn-on-reflection* true)
(ns flower.main
  (:gen-class)
  (:use flower.utils)
  (:require
    [babashka.cli :as cli] ; https://clojurians.slack.com/archives/CLX41ASCS/p1753986315453519
    [babashka.process.pprint]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [flower.beholder]
    [flower.cmd :as cmd]
    [flower.defaults]
    [flower.frontmatter :refer [split-frontmatter]]
    [flower.hiccup]
    [flower.utils]
    [flower.reflect]
    [flower.repl :as repl]
    [flower.unsafe :as unsafe]
    [flower.watch]
    [hiccup.util]))

(def VERSION "0.0.1")

; CLI and IO

(defn read-json []
  (cmd/read-json *in* "stdin"))

(defn map-json
  "Given a function `f` that transforms a clojure map to a clojure map,
   read the map as JSON from stdin and write it to stdout.
   If any `args` are present, they will be passed after the map."
  [f & args]
  (let [opts (first args)
        before (read-json)
        [after deps] (unsafe/with-drop-bomb
                       #(cmd/with-tracked-deps
                         (fn [] (apply f before args))))]
    (if (:depfile opts)
      (cmd/split-dependencies deps opts)
      (when (seq deps)
        (fatal {:flower/deps deps} "at least one file was accessed, but no depfile path was passed!")))
    (cmd/write-json after *out*)))

(defn no-opts [f & args]
  (fn [& _] (apply f args)))

(defn unknown-command [{:keys [args]}]
  (binding [*cmd* ""]
    (error (str "unrecognized command: '"
                (str/join " " args)
                "' ('help' for help, or 'watch' to build your site)"))
    (System/exit 1)))

(declare dispatch-table)

(defn ->help-1
  "Convert our `dispatch-table` DSL to babashka/format-opts syntax.
  format-opts expects the following input:
  `{:spec [[:option {:parse-opts-opt :val}]]}`"
  [[k v]]
  [(keyword k) (if (map? v) v {})])

; wait can i just do this lmao
  ; (let [table (map #(apply ->bb init-fn %) dispatch-table)
  ;       flat-table (flatten table)]
(defn ->help
  []
  {:spec (->> (for [[ks v] dispatch-table]
                (if (sequential? ks)
                  (for [k ks] (->help-1 [k v]))
                  [(->help-1 [ks v])]))
              (apply concat))})

(defn help []
  (-> (->help) cli/format-opts println))

; disallow infinite sequences, they horribly break debugging.
; 100000 pages is enough for anyone, at that point we hit argv limits anyway.
(def argv-max (if *assert* 1000 100000))
(defn cli-read-json [_opts str] (json/read-str str))

(defn parse-kv [coll s]
  (let [coll (or coll {})
        [k v] (split-once s #"=")
        v (if (some? v) v true)]
    (assoc coll k v)))

; reused for `watch`
(def configure-opts
  {:coerce {:build-dir :string
            :set []}
   :collect {:set parse-kv}})

(def dispatch-table
  {"configure" (merge-deep configure-opts
                           {:fn #(cmd/configure %)
                            :coerce {:list :bool}})
   "split-frontmatter" (no-opts map-json split-frontmatter)
   "join-frontmatter" {:fn #(cmd/join-frontmatter %)
                       :coerce {:path []
                                :out-file :string}
                       :args->opts (concat [:out-file] (repeat argv-max :path))}
   "transform" {:fn #(map-json cmd/transform %)
                :coerce {:depfile :string
                         :out-file :string
                         :all-frontmatter :string
                         :transformers []}
                :spec {:transform-map {:desc "A list of mappings from file extension to command runners"}}
                :collect {:transform-map cli-read-json}
                :args->opts (repeat argv-max :transformers)}
   ; TODO: get rid of this
   "split-sass-dependencies"
     {:fn #(-> (read-json) (cmd/split-sass-dependencies %) println)
      :coerce {:source-file :string}
      :args->opts [:source-file]}
   ["b" "build"] (no-opts cmd/build)
   ["w" "watch"] (merge-deep configure-opts
                             {:fn flower.watch/watch
                              :coerce {:port :number}})
   ["r" "repl"] {:fn flower.repl/repl
                 :coerce {:template :boolean}
                 :args->opts [:template]}
   ["n" "new"] (no-opts flower.defaults/materialize-all)
   ; TODO: get rid of this
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

(defn init-fn [cmd-fn args]
  (alter-var-root (var *cmd*) (constantly (->> args :dispatch first (str " "))))
  (binding [*site* (or (get-in args [:opts :C]) ".")
            flower.reflect/*watching* (boolean (or (= "watch" *cmd*)
                                                   (env "FLOWER_WATCH")))]
    (cmd-fn args)))

(defn ->bb
  "Convert our `dispatch-table` DSL to babashka/dispatch syntax.

  `init` is a function that will run before the dispatched command
  to set up global options. It takes two arguments:
  the function to run inside globals and the parsed options.
  It should pass the options as an argument to the function."
  [[key val]]
  (if (and (vector? key) (seq key))
    (for [cmd key] (->bb [cmd val]))
    (let [cmds (if (string? key) [key] key)
          [my-fn opts] (if (map? val) [(:fn val) val] [val {}])
          wrapped-fn (if (:needs-metadata opts) my-fn #(my-fn (:opts %)))
          bb-map (assoc opts
                        :cmds cmds
                        ; :restrict true
                        :fn #(init-fn wrapped-fn %))]
      bb-map)))

(defn dispatch-cmd
  "Parse the CLI args and dispatch to the appropriate clojure funciton.
  Also registers global options."
  [args]
  (let [table (map ->bb dispatch-table)
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

(defmacro cfg [condition & body]
  (when (eval condition) `(do ~@body)))
(defmacro cfg-not [condition & body]
  (when (not (eval condition)) `(do ~@body)))

; dynamic type checking
(cfg *assert*
  (info "instrumenting type signatures")
  (require
    '[malli.instrument :as mi]
    '[malli.dev.pretty :as pretty])
  (def flower-nss
    ['flower.beholder
     'flower.cmd
     'flower.main
     'flower.eval
     'flower.defaults
     'flower.frontmatter
     'flower.hiccup
     'flower.utils
     'flower.reflect
     'flower.repl
     'flower.watch])
  (mi/collect! {:ns flower-nss})
  (mi/instrument! {:report (pretty/thrower)}))
(cfg-not *assert*
  (info "type assertions disabled"))
