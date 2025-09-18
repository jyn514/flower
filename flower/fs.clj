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

(defn glob
  ([root pattern] (glob root pattern {}))
  ([root pattern {:keys [no-vfs] :as opts}]
   (let [real-opts (dissoc opts :no-vfs)
         real-paths (fs/glob root pattern real-opts)
         vfs-paths (if no-vfs [] (fs/glob (defaults-path root) pattern real-opts))
         ; we do this weird map thing so that we override defaults with real paths
         relative-paths (merge (defaults-map vfs-paths) (id-map real-paths))
         faked-paths (keys relative-paths)
         dirs (filter fs/directory? (vals relative-paths))]
     (set! *dependencies* (union *dependencies* (set dirs)))
     faked-paths)))

(defn read-all-bytes
  ([path] (read-all-bytes path {}))
  ([path {:keys [no-vfs]}]
   (let [rel (if no-vfs path (path-considering-vfs path))]
     ; TODO: does this break ninja if it doesn't exist?
     (set! *dependencies* (conj *dependencies* rel))
     (fs/read-all-bytes rel))))

(defn directory?
  ([path] (directory? path {}))
  ([path {:keys [no-vfs]}]
   ; HACK: this probably should register a dependency, but it sucks to rebuild when any child is created/modified and not just the dir itself :/
   (fs/directory? (if no-vfs path (path-considering-vfs path)))))

; put this last so we don't use it by accident
(defn exists?
  ([path] (exists? path {}))
  ([path {:keys [no-vfs] :as opts}]
   (let [real-opts (dissoc opts :no-vfs)
         rel (if no-vfs path (path-considering-vfs path))]
     (set! *dependencies* (conj *dependencies* rel))
     (fs/exists? rel real-opts))))

; bound as clojure.core/slurp, not flower.fs/slurp-
(defn slurp-
  "Note: unlike built-in clojure.core/slurp, only supports Paths, not URIs.
  Options other than :no-vfs are ignored."
  [& args]
  (String. ^bytes (apply read-all-bytes args)))

