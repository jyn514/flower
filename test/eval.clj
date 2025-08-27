(ns eval
  (:require
   [clojure.string :as str]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :as prop]
   [expectations.clojure.test :as expect :refer [expect more-of]]
   [flower.eval :as eval]
   [parser :refer [gen-sunflower]]))

; (s/def :flower.eval/exception
;   (s/keys :req [:flower.eval/

; (defn stacktrace? [e]
;   (and (= true (:flower/eval (ex-data e)))
;        (some? (sci/stacktrace (ex-cause e)))))

(defn expect-parse-error [e]
  (expect "failed to parse <proptest>:"
          (-> e ex-message (str/split #"\n") first)))

(defn expect-eval-error [e]
  (expect (more-of {:keys [type]
                    :flower/keys [filename span]}
                   keyword? type
                   "<proptest>" filename
                   (more-of [l r]
                            integer? l
                            integer? r) span)
          (ex-data (ex-cause e)) (ex-cause e)))

(defn expect-valid-or-trace [s]
  (try (eval/render-file s "<proptest>")
       (catch clojure.lang.ExceptionInfo e
         (condp #(get %2 %1) (ex-data e)
           :flower/parse (expect-parse-error e)
           :flower/eval (expect-eval-error e)
           (throw e))))) ; fail-fast

(defspec eval-has-stacktrace 100
  (prop/for-all [s gen-sunflower]
    (expect-valid-or-trace s)))
