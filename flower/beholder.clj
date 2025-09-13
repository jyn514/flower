(ns flower.beholder
  (:require
   [babashka.fs :as fs])
  (:import
   [java.nio.file
    FileSystems
    FileVisitResult
    FileVisitor
    Files
    Path
    StandardWatchEventKinds
    WatchEvent
    WatchEvent$Kind
    WatchService]))

(def ^:private events
  {StandardWatchEventKinds/ENTRY_CREATE :create
   StandardWatchEventKinds/ENTRY_DELETE :delete
   StandardWatchEventKinds/ENTRY_MODIFY :modify
   StandardWatchEventKinds/OVERFLOW     :overflow})

(defn- ->EventType [kw]
  (case kw
    :create StandardWatchEventKinds/ENTRY_CREATE
    :delete StandardWatchEventKinds/ENTRY_DELETE
    :modify StandardWatchEventKinds/ENTRY_MODIFY
    :else (throw (ex-info (format "unknown event kind %s" kw)
                          {::kind kw}))))

(defn- ev->kw [^WatchEvent$Kind ev]
  ; can't do a (case) here for mysterious reasons
  (or (get events ev)
      (throw (ex-info (format "unknown event type %s" ev)
                      {::event ev}))))

(defn- register!
  [{:keys [^WatchService watcher registry]} ^Path path root events]
  (.register path watcher events)
  (swap! registry assoc path root))

(defn- watch!
  "Given a handle and a list of paths, watch each path.
   Paths must be in the following format:
   {:path <see babashka.fs/path docs>
    :events [:oneof :create :delete :modify]
    :recursive :bool}"
  [{:keys [paths] :as handle}]
  (doseq [p paths]
    (let [{:keys [events recursive path]
           :or {events [:create :delete :modify]
                recursive true}} p
          events (into-array WatchEvent$Kind (map ->EventType events))
          user-path (fs/path path)
          root (if (fs/directory? user-path) user-path (fs/parent user-path))]
      (when (nil? root)
        (throw (ex-info "didn't get a path to watch!" {::path p})))
      (when-not (fs/directory? root)
        (throw (ex-info "TODO: file watching not supported for now, pass the parent directory with :recursive false" {::path p})))
      (if-not recursive
        (register! handle root root events)
        (Files/walkFileTree root
          (proxy [FileVisitor] []
            (preVisitDirectory [_this dir _attrs]
              (register! handle dir root events)
              FileVisitResult/CONTINUE)))))))

; TODO: handle should implement Closeable
(defn create
  "Returns a handle to a directory watcher that listens to filesystem events at any of the
  `paths`. This watcher is 'lazy' and does not take effect until you call `listen`.

  Paths can be one of the following forms:
  - 'anything babashka.fs/path can convert to a path'. In this case, `opts` are applied to the path, as a default.
  - {:path <path, see above>, :recursive :bool, :events [:set [:oneof :create :delete :modify]]}"
  ([opts & paths]
   (let [[opts paths] (if (map? opts) [opts paths] [{} (conj paths opts)])
         watcher (.newWatchService (FileSystems/getDefault))
         paths (for [p paths
                     :let [path-opts (if (map? p) p (assoc opts :path p))]]
                 (update path-opts :path (comp fs/normalize fs/absolutize)))
         handle {:watcher watcher :paths paths :registry (atom {})}]
     (watch! handle)
     ; TODO: maybe do this lazily so we still have all the info?
     (update handle :paths #(map :path %)))))

(defn- filter-files
  [{:keys [paths]} path]
  (or (some #{path} paths)
      (some #(fs/starts-with? path %) paths)))

(defn wait-next
  "Block until the next event occurs, returning all modified paths.
   Throws if a top-level watched path is deleted."
  [{:keys [^WatchService watcher registry] :as handle}]
  (let [key (.take watcher)
        dir (.watchable key)
        root (get registry dir)
        events (for [^WatchEvent ev (.pollEvents key)
                     :let [path (.context ev)
                           kind (.kind ev)
                           abs (fs/path dir path)]
                     :when (filter-files handle abs)]
                 ; NOTE: this includes :overflow events
                 ; NOTE: :root is nil if this was a file watch
                 {:kind (ev->kw kind) :path abs :root root})
        valid (.reset key)]
    (when (and (not valid) (= dir root))
      ; NOTE: we intentionally throw only for the directory, even if we had originally been passed a filename.
      ; The problem here isn't that the file doesn't exist, the problem is that we can't reregister a watcher if the directory is recreated.
      ; It's possible we could watch the *parent* of any directories to see if the directory is recreated but ... that's quite complicated and it's unclear if it's necessary.
      ; Maybe instead we can make it possible for the user to register new watches at runtime.
      (throw (ex-info "top-level watch path no longer exists and cannot be watched"
                      {:type ::invalid, ::path (.watchable key)})))
    events))

; TODO: backpressure
(defn listen
  "Blocks forever, running `cb` on each incoming event."
  [cb handle]
    (loop [events (wait-next handle)]
      (doseq [ev events]
        (cb ev))
      (recur (wait-next handle))))

(defn- default-err-handler
  [err]
  (binding [*out* *err*]
    (println "beholder:" err))
  true)

(defn listen-async
  "Spawns a new thread that runs `cb` on each incoming event.
   You may register a `error-cb` that runs on exceptions thrown in the thread.
   Return `false` from `error-cb` to indicate the loop should abort.
   By default, errors are printed to stderr and the loop is resumed.

   Returns a Thread. By default, the thread is treated as a 'daemon' thread that
   blocks the JVM from exiting. You may wish to mark it as a 'user' thread
   instead."
   ([cb handle] (listen-async cb handle {}))
   ([cb handle
     {:keys [error-cb daemonize]
      :or {error-cb default-err-handler
           daemonize true}} ]
    (let [thread (Thread.
            (reify Runnable
              (run [_this]
                (loop []
                  (let [err (try (listen cb handle)
                                (catch java.lang.Throwable e
                                  (error-cb e)))]
                    (when err (recur))))))
            "flower.beholder file watcher")]
      (when daemonize (Thread/.setDaemon thread true))
      (.start thread)
      (assoc handle :thread thread))))
