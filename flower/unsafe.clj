(ns flower.unsafe
  (:require
   [babashka.process :as ps]
   [flower.utils :refer [fatal gen-depfile]]))

(def ^:dynamic *drop-bomb* false)
(def ^:dynamic *dependencies* #{})

(defn with-drop-bomb [f]
  (binding [*drop-bomb* false]
    (let [out (f)]
      (when *drop-bomb*
        (fatal "a function in flower.unsafe was called without calling flower.unsafe/register-dependencies!"))
      out)))

(defn system
  "Small wrapper around babashka.process/shell.
   If you call this, you must also call `register-file-dependency!` with a list of the paths you accessed."
   [& args]
   (set! *drop-bomb* true)
   (apply ps/shell args))

(defn register-dependencies!
  "Inform flower that your code accesses these files on disk.
   If you do not access any files, but still use a function in flower.unsafe,
   call this without arguments."
  [& paths]
  (set! *dependencies* (concat *dependencies* paths))
  (set! *drop-bomb* false))

(defn with-tracked-deps [f]
  (binding [*dependencies* #{}]
    (let [out-map (f)]
      ; TODO: should be keyed by output file so we can minimize rebuilds
      [out-map *dependencies*])))

(defn split-dependencies
  [dependencies {:keys [depfile out-file]}]
  (when (some nil? [depfile out-file dependencies])
    (throw (ex-info (str "got <nil> when trying to write a depfile for " out-file) {})))
  (let [formatted (gen-depfile out-file dependencies)]
    (spit depfile formatted)))
