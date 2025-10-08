(set! *warn-on-reflection* true)
(ns flower.main
  (:gen-class)
  (:use flower.utils)
  (:require
   [babashka.cli :as cli] ; https://clojurians.slack.com/archives/CLX41ASCS/p1753986315453519
   [babashka.process.pprint]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
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

(defn git-hash [] (-> "META-INF/resources/flower/git-hash" io/resource slurp))
(defn version [] (format "0.0.1 (%s)" (git-hash)))

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

(defn stop-at-duplicates
  "Given a list and a cutoff for a number of duplicate occurences,
  return [<list stopping at duplicates>, duplicate].
  The duplicate can be `nil`."
  [xs cutoff]
  (loop [seen []
         remaining xs]
    (if (empty? remaining) [seen nil]  ; base case
      (let [[head tail] [(first remaining) (rest remaining)]
            trail (take-last (dec cutoff) seen)]
        (if (and (= (dec cutoff) (count trail)) (apply = head trail))
          [(drop-last cutoff seen) head] ; without last
          (recur (conj seen head) tail))))))

(defn format-args [args->opts]
  (let [[unique dup] (stop-at-duplicates args->opts 3)
        args (for [k unique]
               (str "<" (name k) ">"))
        varargs (if dup (str "[<" (name dup) ">...]") "")]
    (str (str/join " " args) varargs)))

(declare global-spec)
(declare dispatch-table)
(defn help
  [{:keys [args]}]
  (if (empty? args)
    ; global help, no arguments
    (let [rows (concat (cli/opts->table (:spec global-spec)) ; TODO: print defaults
                       (for [[cmd meta] dispatch-table
                             :when (string? cmd)]
                         [cmd (:desc meta)]))]
      (printf "flower %s\n" (version))
      (println "Commands:")
      (println (cli/format-table {:rows rows})))
    ; help for subcommand
    (let [cmd (first args)
          cmd-meta (get dispatch-table cmd)]
      (if (nil? cmd-meta) (help {})  ; unknown command
        (do (println "Usage: flower" cmd (format-args (:args->opts cmd-meta)))
            (println "\n" (:desc cmd-meta) "\n")
            (-> cmd-meta (select-keys [:spec]) cli/format-opts println))))))

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
                 :desc "The directory to treat as your site"}}})

(def dispatch-dsl
   ;; meta commands
  {[] {:fn unknown-cmd :needs-metadata true}
   "help"
   {:fn help
    :needs-metadata true
    :aliases #{"--help" "-h" "/?"}
    :desc "Print this help"}
   "version"
   {:fn (no-opts println (version))
    :aliases #{"--version", "-V"}
    :desc (format "Print flower's version: %s" (version))}

   ;; user-facing commands
   "new"
   {:fn flower.defaults/materialize-all
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

(defn ->bb
  "Convert our `dispatch-table` DSL to babashka/dispatch syntax.

  `init` is a function that will run before the dispatched command
  to set up global options. It takes two arguments:
  the function to run inside globals and the parsed options.
  It should pass the options as an argument to the function."
  [[key {my-fn :fn :keys [needs-metadata] :as opts}]]
  (let [wrapped-fn (if needs-metadata my-fn #(my-fn (:opts %)))
        bb-map (assoc opts
                      :cmd key
                      :fn wrapped-fn)]
  bb-map))


(def dispatch-table
  (->> dispatch-dsl (map ->bb) flatten
       (map (juxt :cmd identity)) (into {})))

(def dispatch-aliases
  (into {} (apply concat (for [[cmd {aliases :aliases}] dispatch-table
                               :when aliases]
                           (for [a aliases] [a cmd])))))

(defn init-fn [cmd-fn cmd-name args]
  (alter-var-root (var *cmd*) (constantly (str " " cmd-name)))
  (binding [*site* (or (get-in args [:opts :C]) ".")
            flower.unsafe/*drop-bomb* false ; for `repl`
            flower.reflect/*watch-port* (env "FLOWER_WATCH")]
    (cmd-fn args)))

  ; this is a really really stupid CLI parser that only handles global options and subcommands
  ; opts = {}
  ; args = iter(args)
  ; for arg in args:
  ;   if any(arg == opt for opt in (:spec global-opts)):
  ;     opts[arg] = next(args)
  ;   else:
  ;     cmd = arg
  ;     break
(defn worlds-worst-cli-parser [args]
  ; TODO: this isn't even a parser lol
  [{} (first args) (rest args)])

(defn dispatch-cmd
  "cli/dispatch with blackjack and hookers.
  Parse the CLI args and dispatch to the appropriate clojure funciton.
  Also registers global options."
  [args]
  ; TODO: this is wrong if a later argument contains -C
  ; I think we can avoid this by merging *all* subcommand's options into a big map so bb knows about them
  (let [[global-opts cmd rest] (worlds-worst-cli-parser args)
        resolved-cmd (get dispatch-aliases cmd cmd)
        cmd-meta (get dispatch-table resolved-cmd)]
    (when-not cmd-meta
      (unknown-cmd {:args args}))
    (let [opts (cli/parse-args rest (dissoc cmd-meta :aliases))
          merged-opts (update opts :opts merge global-opts)]
      (init-fn (:fn cmd-meta) resolved-cmd merged-opts))))

(defn main [& args]
  (try
    (dispatch-cmd args)
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
