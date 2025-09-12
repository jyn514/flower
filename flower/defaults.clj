(ns flower.defaults
  (:use [flower.utils])
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.java.io :as io]))

; VFS

; TODO: configurable build-dir
(defn defaults-path [relative]
  (fs/path *site* ".build" "defaults" relative))

(defn path-considering-vfs [path]
  (let [vfs (defaults-path path)
        rel (if (and (not (fs/exists? path))
                     (fs/exists? vfs))
              vfs path)]
    rel))

(defn with-vfs [path f]
  (f (path-considering-vfs path)))

; materialization

(def flower-defaults "META-INF/resources/flower/defaults/")

(def all-defaults
  (let [manifest (-> (str flower-defaults "MANIFEST.txt") io/resource slurp)
        files (str/split manifest #"\n")
        contents (map #(->> % (str flower-defaults) io/resource slurp .getBytes) files)]
    (zipmap files contents)))

(defn materialize
  [path bytes]
  (if (fs/exists? path)
    (warn (fmt "'${path}' already exists, skipping"))
    (do (some-> (fs/parent path) fs/create-dirs)
      ; :truncate false avoids TOCTOU by exiting with an error
      ; not as nice as a warning but this should basically never happen
      (fs/write-bytes path bytes {:truncate-existing false}))))

(defn materialize-all [{}]
  (doseq [[p bytes] all-defaults]
    (let [dst (if (or (= "flower.edn" p)
                      (= "pages" (-> p fs/components first str)))
                (fs/path *site* p)
                (defaults-path p))]
      (materialize dst bytes))))
