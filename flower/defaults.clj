(ns flower.defaults
  (:use [flower utils] [flower.internal.utils])
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def flower-defaults "META-INF/resources/flower/defaults/")

(def all-defaults
  (let [manifest (-> (str flower-defaults "MANIFEST.txt") io/resource slurp)
        files (str/split manifest #"\n")
        contents (map #(->> % (str flower-defaults) io/resource slurp .getBytes) files)]
    (zipmap files contents)))

(defn materialize
  [path bytes]
  ; TODO: skip build.clj, that should be merged by build/generate instead of overwritten
  (if (fs/exists? path)
    (warn (fmt "'${path}' already exists, skipping"))
    (do (some-> (fs/parent path) fs/create-dirs)
      ; :truncate false avoids TOCTOU by exiting with an error
      ; not as nice as a warning but this should basically never happen
      (fs/write-bytes path bytes {:truncate-existing false}))))

(defn materialize-all []
  (doseq [[p bytes] all-defaults] (materialize (str *site* "/" p) bytes)))
