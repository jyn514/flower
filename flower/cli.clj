(ns flower.cli
  (:use flower.utils)
  (:require
   [babashka.cli :as cli]
   ; https://clojurians.slack.com/archives/CLX41ASCS/p1753986315453519
   [babashka.process.pprint]
   [clojure.string :as str]))

;; helpers for use with :fn

(defn no-opts [f & args]
  (fn [& _] (apply f args)))

(defn parse-kv [coll s]
  (let [coll (or coll {})
        [k v] (split-once s #"=")
        v (if (some? v) v true)]
    (assoc coll k v)))

; disallow infinite sequences, they horribly break debugging.
; 100000 pages is enough for anyone, at that point we hit argv limits anyway.
(def argv-max (if *assert* 1000 100000))

;; internals

(defn- stop-at-duplicates
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

(defn- format-args [args->opts]
  (let [[unique dup] (stop-at-duplicates args->opts 3)
        args (for [k unique]
               (str "<" (name k) ">"))
        varargs (if dup (str "[<" (name dup) ">...]") "")]
    (str (str/join " " args) varargs)))

(defn- dispatch-aliases [dispatch-table]
  (into {} (apply concat (for [[cmd {aliases :aliases}] dispatch-table
                               :when aliases]
                           (for [a aliases] [a cmd])))))

; this is a really really stupid CLI parser that only handles global options and subcommands
; opts = {}
; args = iter(args)
; for arg in args:
;   if any(arg == opt for opt in (:spec global-opts)):
;     opts[arg] = next(args)
;   else:
;     cmd = arg
;     break
(defn- worlds-worst-cli-parser [args]
  ; TODO: this isn't even a parser lol
  [{} (first args) (rest args)])

(defn- ->bb
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

;; API

(defn help
  [{:keys [global-spec dispatch-table args]}]
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

(defn dispatch-cmd
  "cli/dispatch with blackjack and hookers.
  Parse the CLI args and dispatch to the appropriate clojure funciton.
  Also registers global options."
  [{:keys [args dispatch-table init-fn unknown-cmd]}]
  ; TODO: this is wrong if a later argument contains -C
  ; I think we can avoid this by merging *all* subcommand's options into a big map so bb knows about them
  (let [[global-opts cmd rest] (worlds-worst-cli-parser args)
        resolved-cmd (get (dispatch-aliases dispatch-table) cmd cmd)
        cmd-meta (get dispatch-table resolved-cmd)]
    (when-not cmd-meta
      (unknown-cmd {:args args}))
    (let [opts (cli/parse-args rest (dissoc cmd-meta :aliases))
          merged-opts (update opts :opts merge global-opts)]
      (alter-var-root (var *cmd*) (constantly (str " " resolved-cmd)))
      (init-fn (:fn cmd-meta) resolved-cmd merged-opts))))

(defn make-dispatch-table [dispatch-dsl]
  (->> dispatch-dsl (map ->bb) flatten
       (map (juxt :cmd identity)) (into {})))
