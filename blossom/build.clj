; NOTE: everything here must be possible to run in a sandbox with read-only access to the filesystem

(ns blossom.build
  (:use [blossom.utils])
  (:require [clojure.string :as str]))

(def ^:private ^:dynamic *ninja* {})
(def ^:private nl "\n")

(defn- upd [k f & args]
  (alter-var-root #'*ninja* #(apply update % k f args)))

(defn rule [opts]
  (upd :rules conj opts))

(defn- variable [key val] (fmt "  ${key} = ${val}"))
(defn- gen-rule [opts]
  (inspect (str "rule " (:name opts) nl
       (variable "command" (:command opts))
     (if (contains? opts :description)
       (variable "description" (:description opts))))))

(defn generate []
  (let [mapper (fn [[k v]]
    (case k
      :rules (map gen-rule v)))]
    (->> *ninja* (map mapper) flatten str/join)))
