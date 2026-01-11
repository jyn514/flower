(ns flower.bin.sunflower
  (:gen-class)
  (:use flower.utils)
  (:require
   [flower.cli :as cli
    :refer [dispatch-cmd help make-dispatch-table no-opts parse-kv run-tracked]]
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

(declare dispatch-table)
(def dispatch-dsl
   ;; meta commands
  (merge cli/render-spec
         {[] {:fn unknown-cmd :needs-metadata true}
          "help"
          {:fn #(help (assoc % :global-spec {} :dispatch-table dispatch-table))
           :needs-metadata true
           :aliases #{"--help" "-h" "/?"}
           :desc "Print this help"}
          "version"
          {:fn (no-opts println (version))
           :aliases #{"--version", "-V"}
           :desc (format "Print sunflower's version: %s" (version))}}))

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
