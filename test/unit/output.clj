(ns test.unit.output
  (:require
   [clojure.test :refer [deftest]]
   [flower.main :as flower]
   [test.snapshot :as snapshot]))

(defn fake-main [args]
  (with-out-str
    (binding [*err* *out*]
      (try
        (flower/dispatch-cmd args)
        (catch clojure.lang.ExceptionInfo e
          (when-not (:flower.main/silent (ex-data e))
            (throw e)))))))

(defn dispatch [args]
  (with-out-str
    (flower/dispatch-cmd args)))

; env FLOWER_UPDATE_SNAPSHOTS=1 clojure -M:test --focus test.unit.output
(deftest cli-error
  ; TODO: this doesn't rebind `*cmd*` properly on file reloads
  (snapshot/expect (fake-main ["x"]) "unknown-cmd")
  (snapshot/expect (dispatch ["-h"]) "help-opt")
  (snapshot/expect (dispatch ["help"]) "help-cmd")
  (snapshot/expect (dispatch ["help" "x"]) "help-unknown")
  (snapshot/expect (dispatch ["help" "build"]) "help-build")
  (snapshot/expect (dispatch ["help" "transform"]) "help-transform"))
