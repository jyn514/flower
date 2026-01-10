(ns test.unit.output
  (:require
   [clj-commons.ansi :as ansi]
   [clojure.test :refer [deftest]]
   [flower.bin.sunflower :as sunflower]
   [flower.main :as flower]
   [test.snapshot :as snapshot]))

(defn test-main [main-fn args]
  (with-out-str
    (binding [*err* *out*]
      (try
        (main-fn args)
        (catch clojure.lang.ExceptionInfo e
          (when-not (:flower.main/silent (ex-data e))
            (throw e)))))))

(defn flower-main [args] (test-main flower/main-no-error-handling args))
(defn sunflower-main [args] (test-main sunflower/main-no-error-handling args))

; env FLOWER_UPDATE_SNAPSHOTS=1 clojure -M:test --focus test.unit.output
(deftest cli-error
  (binding [snapshot/*update* true
            ansi/*color-enabled* true]
    (snapshot/expect (flower-main ["x"]) "unknown-cmd")
    (snapshot/expect (flower-main ["-h"]) "help-opt")
    (snapshot/expect (flower-main ["help"]) "help-cmd")
    (snapshot/expect (flower-main ["help" "x"]) "help-unknown")
    (snapshot/expect (flower-main ["help" "b"]) "help-build")
    (snapshot/expect (flower-main ["help" "build"]) "help-build")
    (snapshot/expect (flower-main ["help" "transform"]) "help-transform")
    (snapshot/expect (sunflower-main ["render"]) "help-render-missing-arg")
    (snapshot/expect (sunflower-main ["render" "--help"]) "help-render-explicit")))
