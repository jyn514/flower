; Portions Copyright Brian Hurlow
; https://github.com/bhurlow/clj-livereload/commit/fe8c6fb296b2f3eaf654dff9f7a9b7b76fe427f5

(ns flower.watch
  (:use flower.utils)
  (:import [java.util Timer TimerTask])
  (:require
   [babashka.fs :as fs]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.stacktrace]
   [clojure.string :as str]
   [flower.http-server :as http-server]
   [flower.beholder :as behold]
   [flower.cmd :as cmd]
   [org.httpkit.server :as wss]))

; file watcher

; https://gist.github.com/oliyh/0c1da9beab43766ae2a6abc9507e732a
(defn debounce
  "Given a channel `in` and a debounce period in `ms`,
   return a channel that emits the most recent value in `in` every `ms`.
   If multiple messages come through `in` within a single period,
   all but the last are discarded.
   New messages during the debounce period reset the timer."
   [f ^long ms]
   (let [timer (Timer.)
         task (atom nil)]
     (fn [& args]
       (let [new-task (proxy [TimerTask] []
                        (run []
                          (apply f args)
                          (reset! task nil)
                          (.purge timer)))
             old ^TimerTask @task]
         ; NOTE: we never retry this;
         ; an outdated value means we already scheduled a rerun,
         ; and we don't promise that all events get through.
         (when (compare-and-set! task old new-task)
           (when old (.cancel old))
           ; TODO: isn't there a race condition here still?
           (.schedule timer new-task ms))))))

(defn watch-files
  ([cb paths] (watch-files cb paths {}))
  ([cb paths {:keys [period] :as opts}]
   (let [debouncer (if (nil? period) cb (debounce cb period))
         handle (apply behold/create (dissoc opts :period) paths)]
     (behold/listen-async debouncer handle))))

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
  [& {:keys [dir port period]}]
  (watch-files #(on-output-change (assoc % :build-dir (fs/real-path dir)))
               [(fs/file-name dir)]
               {:period period :recursive true})
  (wss/run-server handler {:port port}))

; http-server
;
; (defn serve-404 [req dir]
;   (-> (str dir "/404.html") file-response
;       (content-type "text/html")
;       (status 404)
;       (cond-> (= (:request-method req) :head) (assoc :body nil))))
;
; (defn serve-static [dir port]
;   (wss/run-server (compojure/routes 
;                     (files "/" {:root dir})
;                     #(serve-404 % dir)
;                     ; (not-found ()
;                     ){:port port}))

; ninja file watcher

(defn rerun-ninja [opts {:keys [kind path]}]
  ; TODO: figure out if we need to avoid rerunning if ninja is already running
  (when path (println kind (str path)))
  ; ninja can't handle file deletes. generate a new build plan for it.
  ; TODO: delete all the outputs of the deleted file;
  ; you can get a list with `ninja -t query`
  ; TODO: document that if you delete a file and aren't running `flower watch`, you need to do a full rebuild
  (when (= :delete type) (cmd/run-configure opts))
  (run-non-fatal {:extra-env {"FLOWER_WATCH" "1"}} "ninja"))

(defn watch-ninja [opts debounce]
  ; TODO: decide whether to interrupt ninja on changes
  ; definitely shouldn't for anything in `build`
  ; TODO: filter `-t inputs` to only those needed for outputs in `out-dir`
  ; actually no this is fine as-is
  ; TODO: this doesn't notice files that were added after the watch started
  ;       we can mostly work around this if we watch whole directories, i think?
  ; TODO: this doesn't notice files that are only listed in depfiles
  (let [all-inputs (parse-ninja "ninja -t inputs --no-shell-escape")
        temp-file? #(str/starts-with? % (str (:build-dir opts) "/"))
        ; TODO: reconsider if we actually want to filter out build.ninja
        ; also this will be wrong when *site* is set
        important? #(not (or (temp-file? %) (= "build.ninja" %)))
        ; templates are a workaround for not noticing depfiles
        important-inputs (concat [{:path "templates" :recursive true}]
                                 (filter important? all-inputs))
        watcher (watch-files #(rerun-ninja opts %) important-inputs
                             {:period debounce :recursive false})]
    ; run once at startup
    (future (rerun-ninja opts {}))
    watcher))

; api

; TODO: this is the wrong interface, out-dir and build-dir should use flower.edn instead
(defn watch
  [& {:keys [port out-dir debounce-period]
      :or {port 8090
           out-dir "public"
           ; ms
           debounce-period 100}
      :as opts}]
  (println "Run `flower configure`")
  (cmd/run-configure opts)
  ; ninja might not have run yet; create an out dir anyway so we can watch it.
  (fs/create-dirs out-dir)
  ; prints out its own progress info
  (http-server/serve {:dir out-dir :port port})
  (println "Starting live reload watcher for" out-dir)
  (live-reload {:dir out-dir :port 35729 :period debounce-period})
  ; Run this last since ninja emits its own output
  (println "Starting ninja watcher for `cd" *site* "&& ninja -t inputs`"
           "with debounce period" debounce-period)
  (watch-ninja opts debounce-period)
  ; Block forever
  @(promise))

