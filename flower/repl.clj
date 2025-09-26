(ns flower.repl
  (:require
   [babashka.fs :as fs]
   [clojure.main]
   [flower.eval :as eval]
   [flower.reflect :as reflect]
   [flower.stacktrace :refer [print-trace]]
   [flower.utils :refer [state-dir]])
  (:import
   (org.jline.reader
    History
    LineReader
    LineReader$Option
    LineReaderBuilder)
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

(declare specials)
(defn print-help [_template]
  (println "help is still a WIP, sorry")
  (doseq [[k v] specials]
     (printf "  %s\t\t\t%s" k (:help v)))
  (println))

(def specials
  {:help {:help "Print this help"
          :fn print-help}})

(defn flower-eval [template]
  (let [evaluator (if template
                      #(eval/preprocess-sunflower % "<repl>")
                      ; TODO: bind *e
                      #(eval/eval-form (eval/parse-string %) {:src %}))]
    (fn [str]
      (if-let [spec (and (= \: (first str)) ((-> str (subs 1) keyword) specials))]
        ((:fn spec) template)
        (evaluator str)))))

; TODO: this only supports page mode. support transform mode too.
(defn repl
  [{:keys [template]}]
  (binding [eval/*cx* (eval/create-sci-cx "<repl>")
            reflect/*dependencies* #{}
            *reader* (make-reader template)]
    ; TODO: doesn't work because shutdown hooks can't see thread-locals
    ; (.addShutdownHook (Runtime/getRuntime)
    ;                   (Thread. #(.. *reader* getHistory save)))
    (let [desc (if template "template language" "clojure")]
      (printf "Flower %s repl (:help for help)\n" desc)
      (clojure.main/repl :prompt (fn []) ; handled by readline
                         :eval (flower-eval template)
                         ; TODO: bind `sci/*e`
                         :caught #(print-trace % (not template))
                         :read readline))))
