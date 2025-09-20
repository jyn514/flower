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
   [flower.frontmatter]
   [flower.hiccup]
   [flower.reflect]
   [flower.repl]
   [flower.stacktrace :refer [print-trace]]
   [flower.unsafe :as unsafe]
   [flower.utils]
   [flower.watch]
   [hiccup.util]))

(def VERSION "0.0.1")

; CLI and IO

(defn run-tracked
  "Given a function `f` that takes `args`, run it in a flower environment that
  does dependency tracking and allows access to `flower.unsafe`."
  [f & args]
  (let [opts (first args)
        [after deps] (unsafe/with-drop-bomb
                       #(cmd/with-tracked-deps
                         (fn [] (apply f args))))]
    (if (:depfile opts)
      (cmd/split-dependencies deps opts)
      (when (seq deps)
        (fatal {:flower/deps deps} "at least one file was accessed, but no depfile path was passed!")))
    after))

(defn no-opts [f & args]
  (fn [& _] (apply f args)))

(defn unknown-command [{:keys [args]}]
  (when (empty? args)
    (eprintln (str "flower " VERSION))
    (eprintln "'flower help' for help")
    (eprintln "'flower watch' to build your site"))

  (when (seq? args)
    (error (str "unrecognized command: '"
                (str/join " " args)
                "' ('flower help' for help)")))
  (System/exit 1))

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

(def default-opts
  {:build-dir ".build"
   :site-dir "."
   :out-dir "public"
   :port 8090})

(def build-dir
  {:build-dir {:coerce :string
               :desc "Set the path for the ninja temporary directory."}})

; reused for `watch`
; TODO: add `--drafts` as an alias for `--set drafts`
(def configure-opts
  (merge build-dir
         {:set
          {:coerce []
           :collect parse-kv
           :desc (str "Parse a key-value option pair and pass it to `build.clj` in `flower.reflect/*metadata*:settings`. "
                      "Settings are enumerated in `flower.edn`; run `flower configure --list` to print them.")}}))

(def dispatch-table
   ;; meta commands
  {[] {:fn unknown-cmd :needs-metadata true}
   ["help" "--help" "-h" "/?"] (no-opts help)
   ["version" "--version"] (no-opts println VERSION)

   ;; user-facing commands
   ["n" "new"]
   {:fn flower.defaults/materialize-all
    :args->opts [:site-dir]
    :spec (merge build-dir
                 {:site-dir {:coerce :string
                             :desc "The directory in which to create a new flower site. "}})}
   ["c" "configure"]
   {:fn #(cmd/configure %)
    :spec (merge-deep configure-opts
                      {:list {:coerce :bool
                              :desc "List all settings configured in `flower.edn`."}})}
   ["b" "build"]
   {:fn #(cmd/build %)
    :spec configure-opts}

   ["w" "watch"]
   {:fn flower.watch/watch
    :spec (merge configure-opts
                 ; TODO: support `-i/--interface`
                 {:port {:coerce :number
                         :alias :p
                         :desc "The TCP port for the HTTP server to listen on."}})}
   ["r" "repl"]
   {:fn flower.repl/repl
    :args->opts [:template]
    :spec {:template {:coerce :boolean
                      :desc (str "Whether to use a 'template' REPL, where input is treated as the sunflower template language. "
                                 "By default, input is treated as Clojure code.")}}}

   ;; dataflow commands
   ; TODO: *-frontmatter can probably both be transformers
   ; https://codeberg.org/jyn514/flower/issues/58
   "split-frontmatter"
   {:fn #(run-tracked cmd/split-frontmatter %)
    :spec {:filename {:coerce :string
                      :desc "Split a page into a {frontmatter, content} JSON map."}}}

   "join-frontmatter"
   {:fn #(cmd/join-frontmatter %)
    :args->opts (concat [:out-file] (repeat argv-max :path))
    :spec {:path {:coerce []
                  :desc "A list of files whose frontmatter will be joined together into a cache."}
           :out-file {:coerce :string
                      :desc "The file path of the output cache."}}}

   "transform"
   {:fn #(run-tracked cmd/transform %)
    :args->opts (repeat argv-max :transformers)
    :spec {:raw-input {:coerce :boolean
                       :alias :R
                       :desc "Treat the input as a raw string, not a JSON object."}
           :raw-output {:coerce :boolean
                        :alias :r
                        :desc (str "Serialize the output directly with (str), not as a JSON object. "
                                   "In other words, trust the transformer to determine the output format.")}
           :depfile {:coerce :string
                     :desc "Path in which to store a dependency file, used by ninja to track rebuilds."}
           :out-file {:coerce :string
                      :desc (str "Path in which to store the output of the transformers. "
                                 ; TODO: this is very silly lol
                                 "Note that `transform` does not actually write to this file, it just uses it for :depfile.")}
           :all-frontmatter {:coerce :string
                             :desc (str "Path to a file storing a JSON object with the frontmatter of all pages in the site. "
                                        "Ignored when `--standalone` is passed.")}
           :standalone {:coerce :boolean
                        :desc "Whether this is a 'standalone' transformer that doesn't need access to all pages in the site."}
           :transform-map {:coerce {}
                           :collect cli-read-json
                           :desc "A list of mappings from file extension to command runners. Currently ignored."}
           :transformers {:coerce []
                          :desc (str "A list of clojure files ('transformers') to run on the input. "
                                     "Transformers are run in the order they are passed, each accepting input from the previous transformer. "
                                     "The input to the first transformer is read from stdin as JSON (but see --raw-input). "
                                     "The output from the last transformer is written to stdout as JSON (but see --raw-output). "
                                     "Within a transformer, *out* is redirected to stderr, "
                                     "allowing it to use println debugging without interfering with data transformations.")}}}})

(defn init-fn [cmd-fn args]
  (alter-var-root (var *cmd*) (constantly (->> args :dispatch first (str " "))))
  (binding [*site* (or (get-in args [:opts :C]) ".")
            flower.unsafe/*drop-bomb* false ; for `repl`
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
        (print-trace e false))
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
