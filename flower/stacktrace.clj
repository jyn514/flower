(ns flower.stacktrace 
  (:use flower.utils)
  (:require
   [clj-commons.ansi :as ansi]
   [clj-commons.format.exceptions :as pretty-exc]
   [sci.core :as sci]))

; constants

(def grey :faint)

; helpers

(def first-error (atom true))
(def first-eval-error (atom true))

(defn any-eval-err [ex]
  (loop [e ex]
    (if (:flower/eval (ex-data e))
      true
      (if-let [cause (ex-cause e)]
        (recur cause)
        false))))

; printing

(defn render-sci-frame [frame]
  (let [{:keys [line column ns name file]
         :or {name "<top-level>"
              file (if (:sci/built-in frame)
                   "<clojure-runtime>"
                   "<bound-host-function>")}} frame
        span (cond
               (and line column) (str " " line ":" column)
               line (str " " line)
               :else "")
        file-color (if (-> frame :file nil?) grey :green)
        [ns-color name-color] (if (-> frame :name nil?)
                                [nil grey] [:yellow :bold.yellow])
        var (str (ansi/compose [ns-color (str ns "/")])
                 (ansi/compose [name-color name]))
        colored-file (ansi/compose [file-color file])]
    (fmt " [${var} ${colored-file}${span}]\n")))

(def builtin-macros
  #{'for 'let 'when 'fn 'loop})

(defn useful? [frame]
  (let [builtin-macro? (and (some #{(:ns frame)} ['clojure.core 'user])
                            (some #{(:name frame)} builtin-macros))
        possibly-useful? (or (:name frame)
                             (:line frame)
                             (not= (:ns frame) 'user))]
    (and possibly-useful? (not builtin-macro?))))

(defn print-sci-trace [e stacktrace dup]
  (let [useful-frames (dedupe (filter useful? stacktrace))]
    (when (and dup (not (instance? clojure.lang.ExceptionInfo dup)))
      (-> dup type pr-str (str ": ") print))
    (apply print
      (ansi/compose [:bold.red (ex-message e)])
      "\n"
      (map render-sci-frame useful-frames))))

(defn print-stack-trace [e]
  (if-let [sci-ex (sci/stacktrace e)]
      ; skip the inner error, sci duplicates messages >:(
      (let [inner (some-> e ex-cause ex-message)
            dup (= inner (ex-message e))]
        (print-sci-trace e sci-ex (when dup (ex-cause e)))
        (when dup
          (-> e ex-cause ex-cause)))
      (do
        ; TODO: make NoSuchFileExceptions relative to *site*
        (let [msg (ex-message e)
              info (ex-data e)
              error (if (some? info)
                      (if (seq msg) msg
                        (if-let [type (:type info)] type
                          ; really don't have much to work with here ...
                          (ex-data e)))
                      (str (pr-str (class e))
                           ": "
                           (ansi/compose [:italic msg])))]
          (println (ansi/compose [:bold.red error])))
        (when-let [file (-> e ex-data :flower/filename)]
          (let [span (-> e ex-data :flower/span)]
            (print "" (render-sci-frame (merge {:ns 'user :file file} span (meta e))))))
          (ex-cause e))))

(defn print-cause-trace [ex]
  (loop [e ex
         first-loop true]
    (when (not first-loop)
      (print (ansi/compose [:red " Caused by: "])))
    (when-let [cause (print-stack-trace e)]
      (recur cause false))))

(defn print-trace [ex transform-repl]
  (print (str "flower" *cmd* ": " (ansi/compose [:red "error: "])))
  ; TODO: env variables suck lmao, do something else
  (if-not (env "FLOWER_HOST_TRACE")
    (do (print-cause-trace ex)
        (when @first-error
          (println (ansi/compose [grey
            "Some details omitted; set the environment variable FLOWER_HOST_TRACE=1 for a full trackback"]))))
    ; TODO: pretty-printer that hides `invoke` if it's not relevant
    ; maybe do this for apply and LazySeq too?
    (pretty-exc/print-exception ex))
    ; (st/print-cause-trace ex))
  (when (and @first-eval-error (not transform-repl) (not (env "FLOWER_DEBUG_EVAL")) (any-eval-err ex))
    (println (ansi/compose [grey
      "Set FLOWER_DEBUG_EVAL=1 to show the desugared clojure code (e.g. for running in `flower repl`)"]))
    (swap! first-eval-error (constantly false)))
  (swap! first-error (constantly false))
  (flush))
