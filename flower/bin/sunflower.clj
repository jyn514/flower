(ns flower.bin.sunflower
  (:gen-class)
  (:use flower.utils)
  (:require
   [flower.cli  :refer [dispatch-cmd help make-dispatch-table no-opts parse-kv
                        run-tracked]]
   [flower.eval :refer [preprocess-sunflower]]
   [flower.frontmatter :refer [print-frontmatter split-frontmatter]]
   [flower.reflect :refer [preprocess-file]]
   [flower.stacktrace :refer [print-trace]]))

(defn unknown-cmd [{:keys [args]}]
  (if (empty? args)
    (do
      (eprintln (str "sunflower " (version)))
      (eprintln "'sunflower help' for help")
      (eprintln "'sunflower render <file>' to render a template file"))
    (binding [*cmd* ""]
      (error (str "unrecognized command: '"
                  (first args)
                  "' ('sunflower help' for help)"))))
  (throw (ex-info "" {:flower.main/silent true})))

(def all-preprocessors
  {"sunflower" preprocess-sunflower})

(defn render [{:keys [filename define] :as opts}]
  (run-tracked
    (fn [_]
      (let [{:keys [content frontmatter]} (split-frontmatter (assoc opts :content (slurp filename)))
            rendered (preprocess-file (assoc (as-map content frontmatter) :locals define))]
        (print-frontmatter frontmatter)
        (println rendered)))
    opts))

(declare dispatch-table)
(def dispatch-dsl
   ;; meta commands
  {[] {:fn unknown-cmd :needs-metadata true}
   "help"
   {:fn #(help (assoc % :global-spec {} :dispatch-table dispatch-table))
    :needs-metadata true
    :aliases #{"--help" "-h" "/?"}
    :desc "Print this help"}
   "version"
   {:fn (no-opts println (version))
    :aliases #{"--version", "-V"}
    :desc (format "Print sunflower's version: %s" (version))}

   ;; user-facing commands
   "render"
   {:fn render
    :aliases #{"r"}
    :desc "Render a page containing a program written in the Sunflower template language into a string."
    :args->opts [:filename]
    :spec {:filename {:coerce :string
                  :desc "The file path to the page."
                  :require true}
           :define {:alias :D
                    :coerce []
                    :collect parse-kv
                    :desc "Define a variable to be set in this run only."}
           :depfile {:coerce :string
                     :desc "Path in which to store a dependency file, used by ninja to track rebuilds."}
           :out-file {:coerce :string
                      :desc (str "Path in which to store the output of the transformers. "
                                 ; TODO: this is very silly lol
                                 "Note that `transform` does not actually write to this file, it just uses it for :depfile.")}}}})

(def dispatch-table (make-dispatch-table dispatch-dsl))

(defn init-fn [cmd-fn resolved-cmd args]
  (cmd-fn args))

(defn main-no-error-handling [args]
  (dispatch-cmd (as-map args dispatch-table init-fn unknown-cmd)))

(defn main [& args]
  (try
    (main-no-error-handling args)
    0
    (catch java.lang.Exception e
      (when-not (:flower.main/silent (ex-data e))
        (binding [*out* *err*]
          (print-trace e false)))
      1)
    (finally
      (shutdown-agents)
      (flush))))

(defn -main [& args]
  (System/exit (apply main args)))
