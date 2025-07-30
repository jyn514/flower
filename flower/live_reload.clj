; Portions Copyright Brian Hurlow
; https://github.com/bhurlow/clj-livereload/commit/fe8c6fb296b2f3eaf654dff9f7a9b7b76fe427f5

(ns flower.live-reload
  (:use flower.utils)
  (:require [org.httpkit.server :as wss]
            [nextjournal.beholder :as behold]
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
  (println "websocket disconnected" (str ch))
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
    ; TODO: this doesn't work outside of uberjars
    "/livereload.js" {:body (slurp (io/resource livereload-js))
                      :headers {"Content-Type" "application/javascript"}}
    "/livereload" (if (:websocket? req) (start-wss req)
                    {:status 400 :body "Expected a websocket connection\r\n"
                     :headers {}})
    {:status 404 :body "Not Found\r\n" :headers {}}))

; file watcher

; NOTE: does *not* run on changes to metadata (e.g. modification time)
(defn- on-file-change
  [{:keys [type path] :as args}]
  (if-not (contains? [:delete :overflow] type)
    (doseq [ch @channels]
      (if (wss/open? ch)
        (wss/send! ch (json/write-str (reload-msg path)))
        (on-close ch)))))

; api

(def default-port 35729)

(defn listen
  [& {:keys [dir] :as opts
      :or {dir "public"}}]
  (let [opts (merge {:port default-port} opts)]
    (behold/watch on-file-change dir)
    (wss/run-server handler opts)))
