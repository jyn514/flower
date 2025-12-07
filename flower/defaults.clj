(ns flower.defaults
  (:use [flower.utils])
  (:import [java.nio.file StandardOpenOption])
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.set :refer [union]]
   [clojure.string :as str]))

; VFS

(defn always-materialize? [path]
  (or (some #{path} #{"flower.edn" "expressions/constants.clj"})
      (some #{(-> path fs/components first str)} #{"pages" "templates" "static"})))

; TODO: configurable build-dir
(defn defaults-path [relative]
  (fs/path *site* ".build" "defaults" relative))

(defn- vfs-path
  "Different from defaults-path because some files are never in .build.
   Different from path-considering-vfs because it doesn't look at files on disk to make a decision."
   [rel]
   (if (always-materialize? rel)
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

(def all-defaults
  (let [manifest (load-resource "defaults/MANIFEST.txt")
        files (str/split manifest #"\n")
        contents (map #(->> % (str "defaults/") load-resource-bytes) files)]
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
  (some-> (fs/parent path) fs/create-dirs)
  ; CREATE_NEW avoids TOCTOU by exiting with an error
  ; not as nice as a warning but this should basically never happen
  (fs/write-bytes path bytes {StandardOpenOption/CREATE_NEW true}))

(defn init [& _]
  (let [existing-site (fs/exists? (fs/path *site* "flower.edn"))
        version-path (fs/path *site* ".build" "version")
        recorded-version (try-slurp version-path)
        hash (git-hash)]
    (when-not (= hash recorded-version)
      (doseq [[p bytes] all-defaults]
        ; skip pages/ and templates/ for existing sites
        ; see https://codeberg.org/jyn514/flower/issues/71
        (when-not (and existing-site (always-materialize? p))
          (materialize (vfs-path p) bytes)))
      (materialize (ninja-path) (load-resource-bytes "ninja"))
      (fs/set-posix-file-permissions (ninja-path) "rwxrwx---")
      (materialize version-path (.getBytes hash)))))
