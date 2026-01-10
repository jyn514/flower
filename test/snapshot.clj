(ns test.snapshot 
  (:require
   [babashka.fs :as fs]
   [clojure.repl :refer [demunge]]
   [clojure.test :as t]
   #_[flower.reflect :refer [*dependencies*]]))

(def ^:dynamic *assert-fn* nil)
(def ^:dynamic *update* (System/getenv "FLOWER_UPDATE_SNAPSHOTS"))

(def ^:private assertion-counts (atom {}))
(defn on-reload [cx]
  (binding [*out* *err*] (println "running reload hooks"))
  (reset! assertion-counts {})
  ; (alter-var-root #'*dependencies* (constantly #{}))
  cx)

; https://groups.google.com/g/clojure/c/Zpc2yaZDxqA/m/GbmgHK5RAwAJ
(defmacro get-calling-fn []
  `(.getCallerClass (java.lang.StackWalker/getInstance java.lang.StackWalker$Option/RETAIN_CLASS_REFERENCE)))

(defn- fn-name [fn-class]
  ; TODO: `first` here might be wrong?
  (or (-> t/*testing-vars* first meta :name str)
      (-> fn-class .getName demunge)))

(defn- fn-path [fn-class]
  (some-> (.. fn-class getProtectionDomain getCodeSource getLocation) .getPath))

(defn snapshot-path* [caller-class name]
  (let [dir (if-let [p (fn-path caller-class)]
              (fs/parent p) ".")
        snapshot-dir (fs/path dir "snapshots")]
    (fs/create-dirs snapshot-dir)
    (str (fs/path snapshot-dir (str name ".txt")))))

(defn snapshot-name* [caller-class]
  (let [caller (fn-name caller-class)
        count-map (swap! assertion-counts update caller
                         #(if % (inc %) 1))
        count (get count-map caller)]
        (str caller "@" count)))

(defmacro snapshot-path
  ([name] `(snapshot-path* (get-calling-fn) ~name)))

(defn expect* [actual path name]
  (if (or *update* (not (fs/exists? path)))
    (do 
      (println "create snapshot @" path)
      (t/is true)
      (spit path actual))
    (let [expected (slurp path)
          msg (str "Snapshot test " name " failed. Use `(binding *update* true)` or FLOWER_UPDATE_SNAPSHOTS=1 to update.")]
      (if *assert-fn*
        (*assert-fn* expected actual msg)
        (t/is (= expected actual) msg)))))

(defmacro expect
  ([actual] `(expect ~actual (snapshot-name* (get-calling-fn))))
  ([actual name] `(expect* ~actual (snapshot-path ~name) ~name)))
