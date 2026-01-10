(set! *warn-on-reflection* true)
(ns flower.main
  (:gen-class)
  (:use flower.utils flower.cli)
  (:require
   [babashka.cli :as cli]
   ; https://clojurians.slack.com/archives/CLX41ASCS/p1753986315453519
   [babashka.process.pprint]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [flower.cmd :as cmd]
   [flower.defaults]
   [flower.frontmatter]
   [flower.hiccup]
   [flower.reflect]
   [flower.repl]
   [flower.spectacle]
   [flower.stacktrace :refer [print-trace]]
   [flower.unsafe :as unsafe]
   [flower.utils]
   [flower.watch]
   [hiccup.util]))

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

(defn unknown-cmd [{:keys [args]}]
  (if (empty? args)
    (do
      (eprintln (str "flower " (version)))
      (eprintln "'flower help' for help")
      (eprintln "'flower watch' to build your site"))
    (binding [*cmd* ""]
      (error (str "unrecognized command: '"
                  (str/join " " args)
                  "' ('flower help' for help)"))))
  (throw (ex-info "" {::silent true})))

(defn cli-read-json [_opts str] (json/read-str str))

(def default-opts
  {:build-dir ".build"
   :out-dir "public"
   :site "."
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

(def global-spec
  {:exec-args default-opts
   :spec {:site {:coerce :string
                 :alias :C
                 :desc "The directory to treat as your site"}
          :root
          {:coerce :string
           :desc "Site URL root."}}})

(declare dispatch-table)
(def dispatch-dsl
   ;; meta commands
  {[] {:fn unknown-cmd :needs-metadata true}
   "help"
   {:fn #(help (assoc % :global-spec global-spec :dispatch-table dispatch-table))
    :needs-metadata true
    :aliases #{"--help" "-h" "/?"}
    :desc "Print this help"}
   "version"
   {:fn (no-opts println (version))
    :aliases #{"--version", "-V"}
    :desc (format "Print flower's version: %s" (version))}

   ;; user-facing commands
   "new"
   {:fn flower.defaults/init
    :aliases #{"n"}
    :desc "Create a new flower site."
    :args->opts [:site-dir]
    :spec (merge build-dir
                 {:site-dir {:coerce :string
                             :desc "The directory in which to create a new flower site."}})}
   "build"
   {:fn #(cmd/build %)
    :aliases #{"b"}
    :desc "Build a flower site."
    :spec configure-opts}

   "watch"
   {:fn flower.watch/watch
    :aliases #{"w"}
    :desc "Build a flower site, serve its content over HTTP, and automatically rebuild on changes."
    :spec (merge configure-opts
                 ; TODO: support `-i/--interface`
                 {:port {:coerce :number
                         :alias :p
                         :desc "The TCP port for the HTTP server to listen on."}})}
   "repl"
   {:fn flower.repl/repl
    :aliases #{"r"}
    :args->opts [:template]
    :desc "Start a REPL with access to the Clojure in your site."
    :spec {:template {:coerce :boolean
                      :desc (str "Whether to use a 'template' REPL, where input is treated as the sunflower template language. "
                                 "By default, input is treated as Clojure code.")}}}

   ;; dataflow commands
   "configure"
   {:fn #(cmd/configure %)
    :aliases #{"c"}
    :desc "Create a build.ninja file by running build.clj."
    :spec (merge-deep configure-opts
                      {:list {:coerce :bool
                              :desc "Instead of creating a build.ninja, list all settings configured in `flower.edn`."}})}
   ; TODO: *-frontmatter can probably both be transformers
   ; https://codeberg.org/jyn514/flower/issues/58
   "split-frontmatter"
   {:fn #(run-tracked cmd/split-frontmatter %)
    :desc "Split a page into a {frontmatter, content} JSON map."
    :spec {:filename {:coerce :string
                      :desc "Path to the page"}}}

   "join-frontmatter"
   {:fn #(cmd/join-frontmatter %)
    :args->opts (concat [:out-file] (repeat argv-max :path))
    :desc "Join many different files containing JSON maps into a single file containing only their :frontmatter keys."
    :spec {:path {:coerce []
                  :desc "List of files to join"}
           :out-file {:coerce :string
                      :desc "Path to the output cache"}}}

   "transform"
   {:fn #(run-tracked cmd/transform %)
    :args->opts (repeat argv-max :transformers)
    :desc (str "Given an input on stdin, run a set of clojure files ('transformers') transforming it, then print it to stdout. "
               "Transformers are run in the order they are passed, each accepting input from the previous transformer. "
               "The input to the first transformer is read from stdin as JSON (but see --raw-input). "
               "The output from the last transformer is written to stdout as JSON (but see --raw-output). "
               "Within a transformer, *out* is redirected to stderr, "
               "allowing it to use println debugging without interfering with data transformations.")
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
                          :desc "List of transformers to run on the input."}}}})

(def dispatch-table (make-dispatch-table dispatch-dsl))

(defn init-fn [cmd-fn _cmd-name args]
  (binding [*site* (or (get-in args [:opts :site]) ".")
            flower.unsafe/*drop-bomb* false ; for `repl`
            flower.reflect/*root* (or (get-in args [:opts :root]) (env "FLOWER_ROOT") "/")
            flower.reflect/*watch-port* (env "FLOWER_WATCH")]
    (cmd-fn args)))

(defn main [& args]
  (try
    (dispatch-cmd (as-map args dispatch-table init-fn unknown-cmd))
    0
    (catch java.lang.Exception e
      (when-not (::silent (ex-data e))
        (binding [*out* *err*]
          (print-trace e false)))
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
       ['flower.spectacle
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
