; Portions Copyright Brian Hurlow
; https://github.com/bhurlow/clj-livereload/commit/fe8c6fb296b2f3eaf654dff9f7a9b7b76fe427f5

(ns flower.watch
  (:use flower.internal.utils)
  (:require
   [babashka.fs :as fs]
   [babashka.http-server :as http-server]
   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.stacktrace]
   [clojure.string :as str]
   [flower.cmd :as cmd]
   [flower.beholder :as behold]
   [org.httpkit.server :as wss]))

; file watcher

(defn on-file-change
  [cb paths event]
    ; behold doesn't support file filters, only directory filters. implement them ourselves.
    (when (contains? paths (:path event))
         (cb event)))

(defn to-dir [path]
  (let [dir (if (fs/directory? path) path (fs/parent path))]
    (-> dir fs/real-path str)))

(defn watch-files
  [cb paths]
  (let [abs-paths (set (map #(fs/real-path % {:nofollow-links true}) paths))
        dirs (set (map to-dir abs-paths))]
    (apply behold/watch #(on-file-change cb abs-paths %) dirs)))

; live-reload proto

(def hello-message
  {:command "hello"
   ; zola and tiny-rl only support this version, so I think it's safe to do the same
   :protocols ["http://livereload.com/protocols/official-7"]
   :serverName "flower watch"})

(defn- reload-msg
  [path]
  {:command "reload"
   :path (str "/" path)
   :liveCSS true})

; WSS server

(def channels (atom #{}))

(defn- on-open [ch]
  (println "new websocket connected" (str ch))
  (swap! channels conj ch))
(defn- on-close [ch status]
  (println "websocket disconnected" (str ch) status)
  (swap! channels disj ch))
(defn- on-receive [ch data]
  (let [cmd (get (json/read-str data) "command")]
    (when (= "hello" cmd)
      (wss/send! ch (json/write-str hello-message)))))

(defn- start-wss [req]
  (wss/as-channel req
    {:on-open on-open
     :on-receive on-receive
     :on-close on-close}))

; live-reload HTTP server
(def livereload-js "META-INF/resources/flower/watch/livereload-4.0.2/livereload.js")

(defn- handler [req]
  (case (:uri req)
    "/livereload.js" {:body (slurp (io/resource livereload-js))
                      :headers {"Content-Type" "application/javascript"}}
    "/livereload" (if (:websocket? req) (start-wss req)
                    {:status 400 :body "Expected a websocket connection\r\n"
                     :headers {}})
    {:status 404 :body "Not Found\r\n" :headers {}}))

; live-reload listener

(defn- on-output-change
  [{:keys [type path build-dir]}]
  (when-not (some #{type} [:delete :overflow])
    ; TODO: strip-prefix
    (doseq [ch @channels]
      ; TODO: only send this message if the :url matches the changed path
      (if (wss/open? ch)
        (->> (fs/relativize build-dir path) fs/file-name reload-msg json/write-str (wss/send! ch))
        (on-close ch "(unknown reason)")))))

(defn live-reload
  [& {:keys [dir port]}]
  (watch-files #(on-output-change (assoc % :build-dir (fs/real-path dir)))
               [(fs/file-name dir)])
  (wss/run-server handler {:port port}))

; ninja file watcher

(defn rerun-ninja [{:keys [type path]}]
  ; TODO: figure out if we need to avoid rerunning if ninja is already running
  (println type (str path))
  ; ninja can't handle file deletes. generate a new build plan for it.
  ; TODO: delete all the outputs of the deleted file;
  ; you can get a list with `ninja -t query`
  ; TODO: document that if you delete a file and aren't running `flower watch`, you need to do a full rebuild
  ; TODO: don't rebuild immediately if ninja modifies a bunch of intermediate files, it looks weird
  (when (= :delete type) (run-non-fatal "flower configure"))
  (run-non-fatal "ninja"))

(defn watch-ninja [build-dir]
  ; TODO: decide whether to interrupt ninja on changes
  ; definitely shouldn't for anything in `build`
  ; TODO: filter `-t inputs` to only those needed for outputs in `out-dir`
  ; actually no this is fine as-is
  ; TODO: this doesn't notice files that were added after the watch started
  (let [all-inputs (parse-ninja "ninja -t inputs")
        temp-file? #(str/starts-with? % (str build-dir "/"))
        important-inputs (filter #(not (temp-file? %)) all-inputs)
        watcher (watch-files rerun-ninja important-inputs)]
    ; run once at startup
    ; TODO: this doesn't seem to be working?
    (async/go #(rerun-ninja {:type :created :path *site*}))
    watcher))

; api

(defn watch
  [& {:keys [static-port out-dir build-dir]
      :or {static-port 8090
           out-dir "public"
           build-dir ".build"}}]
  (println "Rerun `flower configure`")
  (cmd/configure)
  (println "Starting ninja watcher for `cd" *site* "&& ninja -t inputs`")
  (watch-ninja build-dir)
  ; ninja could have failed, in which case out-dir won't exist.
  ; but we still want to start a server in case it succeeds later.
  ; create a fake directory for it now.
  (fs/create-dirs out-dir)
  (println "Starting live reload watcher for" out-dir)
  ; TODO: this needs to be async oops
  (live-reload {:dir out-dir :port 35729})
  (http-server/exec {:dir out-dir :port static-port}))

