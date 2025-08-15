(ns flower.repl
  (:import (org.jline.terminal TerminalBuilder)
           (org.jline.reader LineReaderBuilder LineReader))
  (:require
    [clojure.main]
    [flower.eval :as eval]))

(def ^:dynamic *reader*)

(defn- make-reader []
  (let [term (.. TerminalBuilder builder (system true) build)]
    (.. LineReaderBuilder builder (terminal term) build)))

(defn- readline
  [fresh exit]
  ; TODO: line-aware
  (try (LineReader/.readLine *reader* "flower=>")
       (catch org.jline.reader.EndOfFileException _ exit)
       (catch org.jline.reader.UserInterruptException _ fresh)))

; TODO: this only supports page mode. support transform mode too.
(defn repl
  []
  (binding [eval/*cx* (eval/create-fs-cx "<repl>")
            *reader* (make-reader)]
    (let [flower-eval #(eval/render-file % "<repl>")]
          (clojure.main/repl :prompt (fn []) ; handled by readline
                             :eval flower-eval
                             :read readline))))
