(ns flower.fs
  (:require
   [babashka.fs :as fs]
   [clojure.set :refer [union]]
   [flower.utils :refer [remove-parent]]
   [flower.defaults :refer [defaults-path path-considering-vfs]]
   [flower.reflect :as reflect :refer [*dependencies*]]))

(defn- id-map [vals]
  (into {} (map (juxt identity identity) vals)))

(defn- defaults-map [paths]
  (into {} (for [p paths] [(remove-parent p 2) p])))

(defn glob [root & opts]
  (let [real-paths (apply fs/glob root opts)
        vfs-paths (apply fs/glob (defaults-path root) opts)
        ; we do this weird map thing so that we override defaults with real paths
        relative-paths (merge (defaults-map vfs-paths) (id-map real-paths))
        faked-paths (keys relative-paths)
        dirs (filter fs/directory? (vals relative-paths))]
   (set! *dependencies* (union *dependencies* (set dirs)))
   faked-paths))

(defn read-all-bytes [path]
  (let [rel (path-considering-vfs path)]
    ; TODO: does this break ninja if it doesn't exist?
    (set! *dependencies* (conj *dependencies* rel))
    (fs/read-all-bytes rel)))

(defn directory? [path]
  ; HACK: this probably should register a dependency, but it sucks to rebuild when any child is created/modified and not just the dir itself :/
  (fs/directory? (path-considering-vfs path)))

; put this last so we don't use it by accident
(defn exists? [path & opts]
  (let [rel (path-considering-vfs path)]
    (set! *dependencies* (conj *dependencies* rel))
    (apply fs/exists? rel opts)))

; bound as clojure.core/slurp, not flower.fs/slurp-
(defn slurp- [path]
  (String. ^bytes (flower.fs/read-all-bytes path)))

