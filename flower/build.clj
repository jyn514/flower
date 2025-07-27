; NOTE: everything here must be possible to run in a sandbox with read-only access to the filesystem

(ns flower.build
  (:use [flower.utils])
  (:require [clojure.string :as str]))

; just to make the compiler happy
; (if (not (resolve '*ninja*))
;   (do (println "hiii")
(def ^:dynamic *ninja*)
;))
(def ^:private nl "\n")

; (defn- upd [k f & args]
;   (alter-var-root #'*ninja* #(apply update % k f args)))
;
; (defn rule [opts]
;   (upd :rules conj opts))

(defn- escape
  "Escape a string for use as a ninja file path.
   See https://ninja-build.org/manual.html#ref_lexer"
  [s] 
  (-> s
      (str/replace "\n" "$n")
      (str/replace " " "$ ")
      (str/replace ":" "$:")
      (str/replace "$" "$$")))

(defn- variable [key val] (fmt "  ${key} = ${val}"))
(defn- gen-rule [opts]
  (inspect (str "rule " (:name opts) nl
       (variable "command" (:command opts))
     (if (contains? opts :description)
       (variable "description" (:description opts))))))

; TODO: support order-only and implicit deps, variables
; `(apply dissoc)` probably gets halfway there
(defn- gen-build [opts]
  (let [join #(->> % (map escape) (str/join " "))
        out (join (:outputs opts))
        in (join (:inputs opts))]
    (str "build " out ": " (:rule opts) " " in)))

(defn generate [ninja]
  (let [mapper (fn [[k v]]
    (case k
      :rules (map gen-rule v)
      :builds (map gen-build v)))
        contents (->> ninja (map mapper) flatten str/join)]
    ; (repl/dir flower.build)
    (println (ns-publics 'flower.build))
    (.write flower.build/*ninja* contents)))
