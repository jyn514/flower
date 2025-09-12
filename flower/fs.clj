(ns flower.fs
  (:require
   [flower.reflect :as reflect :refer [*dependencies*]]
   [babashka.fs :as fs]
   [clojure.set :refer [union]]))

; TODO: VFS

; HACK: this probably should register a dependency, but it sucks to rebuild when any child is created/modified and not just the dir itself :/
(def directory? fs/directory?)

(defn exists? [path & opts]
  (set! *dependencies* (conj *dependencies* path))
  (apply fs/exists? path opts))

(defn glob [& args]
  (let [paths (apply fs/glob args)
        dirs (filter fs/directory? paths)]
   (set! *dependencies* (union *dependencies* (set dirs)))
   paths))

(defn read-all-bytes [path]
  (set! *dependencies* (conj *dependencies* path))
  (fs/read-all-bytes path))

