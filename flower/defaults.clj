(ns flower.defaults
  (:use [flower.utils])
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.set :refer [union]]
   [clojure.string :as str]))

; VFS

; TODO: configurable build-dir
(defn defaults-path [relative]
  (fs/path *site* ".build" "defaults" relative))

(defn- vfs-path
  "Different from defaults-path because some files are never in .build.
   Different from path-considering-vfs because it doesn't look at files on disk to make a decision."
   [rel]
   (if (or (= "flower.edn" rel)
           (some #{(-> rel fs/components first str)} #{"pages" "templates" "static"}))
     (fs/path *site* rel)
     (defaults-path rel)))

(defn path-considering-vfs [path]
  (let [site-path (fs/path *site* path)
        vfs (vfs-path path)
        rel (if (and (not= vfs site-path)
                     (not (fs/exists? site-path))
                     (fs/exists? vfs))
              vfs site-path)]
    rel))

; materialization

(def flower-defaults "META-INF/resources/flower/defaults/")

(def all-defaults
  (let [manifest (-> (str flower-defaults "MANIFEST.txt") io/resource slurp)
        files (str/split manifest #"\n")
        contents (map #(->> % (str flower-defaults) io/resource slurp .getBytes) files)]
    (zipmap files contents)))

; used in flower.reflect
(def all-default-paths
 "A [{:virtual path, :real path}] mapping for all defaults bundled with flower."
 (let [files (set (keys all-defaults))
       dirs (set (filter some? (map fs/parent files)))]
   (into {} (for [p (union files dirs)
                  :let [vfs (vfs-path p)]
                  :when (= vfs (defaults-path p))]
              [(fs/path p) vfs]))))

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
    (materialize (vfs-path p) bytes)))
