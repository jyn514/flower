; Portions Copyright Brian Hurlow
; https://github.com/bhurlow/clj-livereload/commit/fe8c6fb296b2f3eaf654dff9f7a9b7b76fe427f5

(ns flower.live-reload
  (:use flower.utils flower.internal-utils)
  (:require [org.httpkit.server :as wss]
            [nextjournal.beholder :as behold]
            [babashka.http-server :as http-server]
            [babashka.process :as ps]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]))

; proto

(def hello-message
  {:command "hello"
   ; zola and tiny-rl only support this version, so I think it's safe to do the same
   :protocols ["http://livereload.com/protocols/official-7"]
   :serverName "flower live-reload"})

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
    (if (= "hello" cmd)
      (wss/send! ch (json/write-str hello-message)))))

(defn- start-wss [req]
  (wss/as-channel req
    {:on-open on-open
     :on-receive on-receive
     :on-close on-close}))

; HTTP server
(def livereload-js "META-INF/resources/flower/live-reload/livereload-4.0.2/livereload.js")

(defn- handler [req]
  (println (apply format "%s %s" ((juxt :request-method :uri) req)))
  (case (:uri req)
    "/livereload.js" {:body (slurp (io/resource livereload-js))
                      :headers {"Content-Type" "application/javascript"}}
    "/livereload" (if (:websocket? req) (start-wss req)
                    {:status 400 :body "Expected a websocket connection\r\n"
                     :headers {}})
    {:status 404 :body "Not Found\r\n" :headers {}}))

; file watcher

; NOTE: does *not* run on changes to metadata (e.g. modification time)
(defn- on-file-change
  [{:keys [type path]}]
  (if-not (contains? [:delete :overflow] type)
    (doseq [ch @channels]
      (if (wss/open? ch)
        (wss/send! ch (json/write-str (reload-msg path)))
        (on-close ch "(unknown reason)")))))

; live-reload listener

(def default-port 35729)

(defn live-reload
  [& {:keys [dir port]}]
  (behold/watch on-file-change dir)
  (wss/run-server handler {:port port}))

; static file server

; already babashka's default, but we want to print it out nicely
(def default-http-port 8090)

; ninja file watcher

(defn help [] (println "Aaaa"))

(defn watch-ninja [build-dir]
  ; TODO: decide whether to interrupt ninja on changes
  ; definitely shouldn't for anything in `build`
  ; TODO: filter `-t inputs` to only those needed for outputs in `out-dir`
  ; actually no this is fine as-is
  (let [all-inputs (parse-ninja "ninja -t inputs")
        temp-file? #(str/starts-with? (str build-dir "/") %)
        important-inputs (filter #(not (temp-file? %)) all-inputs)
        rerun-ninja #(do (println "rerun ninja") (ps/shell "ninja"))
        dirs ["pages" "templates"]
        watcher (behold/watch help "pages")]
        ; watcher (apply behold/watch rerun-ninja important-inputs)]
    (println "watching" important-inputs)
    watcher))

; api

(defn watch
  [& {:keys [live-reload-port static-port out-dir build-dir change-dir]
      :or {live-reload-port default-port
           static-port default-http-port
           out-dir "public"
           build-dir ".build"}}]
  (println "Starting ninja watcher")
  (watch-ninja build-dir)
  (println "Starting live reload watcher")
  ; TODO: this needs to be async oops
  (live-reload {:dir out-dir :port live-reload-port})
  (println "Starting web server")
  (http-server/exec {:dir out-dir :port static-port}))
