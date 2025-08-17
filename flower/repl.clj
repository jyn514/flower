(ns flower.repl
  (:require
   [clojure.main]
   [clojure.stacktrace :as st]
   [flower.eval :as eval])
  (:import
   (org.jline.reader LineReader LineReaderBuilder)
   (org.jline.terminal TerminalBuilder)))

(def ^:dynamic *reader*)

(defn- make-reader []
  (let [term (.. TerminalBuilder builder (system true) build)]
    (.. LineReaderBuilder builder (terminal term) build)))

(defn- readline
  [fresh exit]
  ; TODO: line-aware
  (try (LineReader/.readLine *reader* "flower=> ")
       (catch org.jline.reader.EndOfFileException _ exit)
       (catch org.jline.reader.UserInterruptException _ fresh)))

(defn print-trace [e]
  (print "flower: error: ")
  ; TODO: env variables suck lmao, do something else
  (if-not (System/getenv "FLOWER_HOST_TRACE")
    (eval/print-cause-trace e)
    ; TODO: pretty-printer that hides `invoke` if it's not relevant
    ; maybe do this for apply and LazySeq too?
    (st/print-cause-trace e))
  (println))

; TODO: this only supports page mode. support transform mode too.
(defn repl
  [{:keys [template]}]
  (binding [eval/*cx* (eval/create-fs-cx "<repl>")
            *reader* (make-reader)]
    (let [flower-eval (if template
                        #(eval/render-file % "<repl>")
                        ; TODO: bind *e
                        #(eval/eval-form % (eval/parse-string %)))]
      (clojure.main/repl :prompt (fn []) ; handled by readline
                         :eval #(try (flower-eval %) 
                                     (catch java.lang.Exception e
                                       (print-trace e)))
                         :read readline))))
