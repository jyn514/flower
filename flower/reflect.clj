(ns flower.reflect
  (:require [clojure.set :refer [union]]
            [babashka.fs :as fs]))

; NOTE: these helpers are exposed to all postprocessors,
; so they must record their file dependencies.

(def ^:dynamic *dependencies* "not for public use" #{})

(defn read-file [path]
  (alter-var-root #'*dependencies* #(union % #{path}))
  (fs/read-all-bytes path))

(defn template [relative-path]
  (read-file (str "templates/" relative-path)))
