; NOTE: everything here must be possible to run in a sandbox with read-only access to the filesystem

(ns flower.build
  (:use [flower.utils])
  (:require [clojure.string :as str]))

; bound by 'configure
(def ^:dynamic *ninja* "not for public use" nil)
(def ^:private nl "\n")

(defn- escape
  "Escape a string for use as a ninja file path.
   See https://ninja-build.org/manual.html#ref_lexer"
  [s] 
  (-> s
      (str/replace "\n" "$n")
      (str/replace " " "$ ")
      (str/replace ":" "$:")
      (str/replace "$" "$$")))

(defn- variable [key val] (fmt "  ${key} = ${val}\n"))
(defn- gen-rule [opts]
  (inspect (str "rule " (:name opts) nl
       (variable "command" (:command opts))
     (if (contains? opts :description)
       (variable "description" (:description opts))))))

(defn- join [xs]
  (let [xs (if (string? xs) [xs] xs)]
    (->> xs (map escape) (str/join " "))))

; TODO: support order-only and implicit deps, variables
; `(apply dissoc)` probably gets halfway there
(defn- gen-build [opts]
  (let [out (join (:outputs opts))
        in (join (:inputs opts))]
    (str "build " out ": " (:rule opts) " " in nl)))

(defn generate [ninja]
  (let [mapper (fn [[k v]]
          (case k
            :rules (conj (map gen-rule v) nl)
            :builds (conj (map gen-build v) nl)))
        contents (->> ninja (map mapper) flatten str/join)]
    (.write flower.build/*ninja* contents)))
