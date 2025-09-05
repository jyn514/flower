(ns flower.unsafe
  (:require
   [babashka.process :as ps]
   [flower.utils :refer [fatal]]
   [flower.reflect :as reflect]))

(def ^:dynamic *drop-bomb* false)

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
  (set! reflect/*dependencies* (concat reflect/*dependencies* paths))
  (set! *drop-bomb* false))
