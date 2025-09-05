(ns flower.repl
  (:require
   [babashka.fs :as fs]
   [clojure.main]
   [clojure.stacktrace :as st]
   [flower.eval :as eval]
   [flower.reflect :as reflect]
   [flower.utils :refer [env state-dir *cmd*]])
  (:import
   (org.jline.reader LineReader LineReader$Option LineReaderBuilder History)
   (org.jline.terminal TerminalBuilder)))

(def ^:dynamic *reader*)

(defn history [template]
  (let [s (state-dir)
        f (if template "template-history" "transformer-history")
        hist (fs/path s f)]
    (fs/create-dirs s)
    hist))

(defn- make-reader [template]
  (let [term (.. TerminalBuilder builder (system true) build)
        reader (.. LineReaderBuilder builder
            (terminal term)
            (variable LineReader/HISTORY_FILE (history template))
            build)]
    ; otherwise it swallows backslashes >:(
    (.setOpt reader LineReader$Option/DISABLE_EVENT_EXPANSION)
    reader))

(defn- readline
  [fresh exit]
  ; TODO: line-aware
  (try (LineReader/.readLine *reader* "flower=> ")
       (catch org.jline.reader.EndOfFileException _
         (-> *reader* LineReader/.getHistory History/.save)
         exit)
       (catch org.jline.reader.UserInterruptException _ fresh)))

(def first-error (atom true))
(def first-eval-error (atom true))

(defn any-eval-err [ex]
  (loop [e ex]
    (if (:flower/eval (ex-data e))
      true
      (if-let [cause (ex-cause e)]
        (recur cause)
        false))))

(defn print-trace [ex transform-repl]
  (print (str "flower" *cmd* ": error: "))
  ; TODO: env variables suck lmao, do something else
  (if-not (env "FLOWER_HOST_TRACE")
    (do (eval/print-cause-trace ex)
        (when @first-error
          (println "Some details omitted; set the environment variable FLOWER_HOST_TRACE=1 for a full trackback")))
    ; TODO: pretty-printer that hides `invoke` if it's not relevant
    ; maybe do this for apply and LazySeq too?
    (st/print-cause-trace ex))
  (when (and @first-eval-error (not transform-repl) (not (env "FLOWER_DEBUG_EVAL")) (any-eval-err ex))
    (println "Set FLOWER_DEBUG_EVAL=1 to show the desugared clojure code (e.g. for running in `flower repl`)")
    (swap! first-eval-error (constantly false)))
  (swap! first-error (constantly false))
  (println))

; TODO: this only supports page mode. support transform mode too.
(defn repl
  [{:keys [template]}]
  (binding [eval/*cx* (eval/create-fs-cx "<repl>")
            reflect/*dependencies* #{}
            *reader* (make-reader template)]
    ; TODO: doesn't work because shutdown hooks can't see thread-locals
    ; (.addShutdownHook (Runtime/getRuntime)
    ;                   (Thread. #(.. *reader* getHistory save)))
    (let [flower-eval (if template
                        #(eval/render-file % "<repl>")
                        ; TODO: bind *e
                        #(eval/eval-form % (eval/parse-string %)))]
      (clojure.main/repl :prompt (fn []) ; handled by readline
                         :eval flower-eval
                         :caught #(print-trace % (not template))
                         :read readline))))
