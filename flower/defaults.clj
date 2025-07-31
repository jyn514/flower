(ns flower.defaults
  (:use [flower.utils])
  (:require [babashka.fs :as fs]
            [flower.build :as build]))

(def all-defaults
  (let [paths (fs/glob "defaults" "**")
        contents (map fs/read-all-bytes paths)
        short-paths (map build/remove-parent paths)]
    (zipmap short-paths contents)))

(defn materialize
  [path bytes]
  ; TODO: skip build.clj, that should be merged by build/generate instead of overwritten
  (if (fs/exists? path)
    (warn (fmt "'${path}' already exists, skipping"))
    ; :truncate false avoids TOCTOU by exiting with an error
    ; not as nice as a warning but this should basically never happen
    (fs/write-bytes path bytes {:truncate-existing false})))

(defn materialize-all [path]
  (doseq [[path bytes] all-defaults] (materialize path bytes)))
