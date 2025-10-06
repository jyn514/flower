; Portions Copyright Brian Hurlow
; https://github.com/bhurlow/clj-livereload/commit/fe8c6fb296b2f3eaf654dff9f7a9b7b76fe427f5

(ns flower.watch
  (:use flower.utils)
  (:require
   [babashka.cli :as cli]
   [babashka.fs :as fs]
   [clj-commons.ansi :as ansi]
   [clojure.data.json :as json]
   [clojure.java.browse :refer [browse-url]]
   [clojure.java.io :as io]
   [clojure.stacktrace]
   [clojure.string :as str]
   [flower.cmd :as cmd]
   [flower.http-server :as http-server]
   [flower.reflect :as reflect]
   [flower.spectacle :as spectacle]
   [org.httpkit.server :as wss])
  (:import
   [java.util Timer TimerTask]
   [org.jline.terminal Terminal TerminalBuilder]))

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
         handle (apply spectacle/create (dissoc opts :period) paths)]
     (spectacle/listen-async debouncer handle))))

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
  #_(println "new websocket connected" (str ch))
  (swap! channels conj ch))
(defn- on-close [ch status]
  #_(println "websocket disconnected" (str ch) status)
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
  ; Do this first so we don't spawn a bunch of file watchers as we retry ports.
  (wss/run-server handler {:port port})
  (watch-files #(on-output-change (assoc % :build-dir (fs/real-path dir)))
               [(fs/file-name dir)]
               {:period period :recursive true}))

; ninja file watcher

(defn rerun-ninja [{:as opts :keys [live-reload-port]} {:keys [kind path]}]
  ; TODO: figure out if we need to avoid rerunning if ninja is already running
  (when path (println kind (str path)))
  ; ninja can't handle file deletes. generate a new build plan for it.
  ; TODO: delete all the outputs of the deleted file;
  ; you can get a list with `ninja -t query`
  ; TODO: document that if you delete a file and aren't running `flower watch`, you need to do a full rebuild
  (when (= :delete kind) (cmd/run-configure opts))
  (run-non-fatal {:extra-env {"FLOWER_WATCH" live-reload-port}} "ninja"))

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
    watcher))

(defn find-port [f default-port {:as opts :keys [port]}]
  (if port
    (do (f opts)  ; if specified explicitly, give a hard error if we can't bind
        port)
    ; otherwise, keep trying until we find an available port
    (try (f (assoc opts :port default-port))
         default-port
         (catch java.net.BindException err
           (warn "failed to bind on port" (str default-port ":") err)
           (find-port f (inc default-port) opts)))))

; interactive event handler

(declare commands)
(defn help [_opts]
  (let [table (for [[k {:keys [desc]}] commands]
                [(pr-str k) desc])]
    (println (cli/format-table {:rows table}))))

(defn no-opts [f & args]
  (fn [_opts] (apply f args)))

(def commands
  {\? {:fn help
       :name "Help"
       :desc "Print this help"}
   \t {:fn (no-opts run-non-fatal "ninja -t targets")
       :name "Targets"
       :desc "Print all build [t]argets (sometimes called 'outputs' or 'artifacts')"}
   \i {:fn (no-opts run-non-fatal "ninja -t inputs")
       :name "Inputs"
       :desc "Print all build [i]nputs"}
   \o {:fn #(browse-url (str "http://localhost:" (:port %)))
       :name "Open"
       :desc "[O]pen your flower site in the browser"}
   \w {:fn (no-opts run-non-fatal "ninja -n -d explain")
       :name "Why"
       :desc "Print all targets that ninja will rebuild next time it is invoked, and [w]hy they will be built."}
   ; TODO: run this automatically
   \m {:fn (no-opts run-non-fatal "ninja -t missingdeps")
       :name "Missing"
       :desc "Show [m]issing dependency edges in the build graph"}
   ; TODO: run this automatically
   \c {:fn (no-opts run-non-fatal "ninja -t cleandead")
       :name "Clean"
       :desc "Delete ('[c]lean') outdated artifacts in the public/ directory"}
   \backspace {:fn #(do
                      (run-non-fatal "ninja -t clean")
                      (rerun-ninja % {}))
               :name "Delete"
               :desc (str "Delete all generated build artifacts and rerun ninja. "
                          "Useful if build.clj has a bug. "
                          "This shouldn't normally be necessary. ")}
   \return {:fn #(rerun-ninja % {})
            :name "Rerun"
            :desc (str "Rerun ninja. "
                       "This can be useful if the file watcher is buggy for some reason. "
                       "Note that this does not force the site to rebuild from scratch. "
                       "Flower always uses the build plan generated by `build.clj` when running ninja.")}})

(defn- make-reader []
  (let [term (.. TerminalBuilder builder (system true) build)
        ; save original term settings
        attrs (.getAttributes term)]
    ; read a single char at a time
    (.enterRawMode term)
    (as-map term attrs)))

(defn watch-input [opts]
  (let [{:keys [^Terminal term attrs]} (make-reader)
        reader (.reader term)]
    (try
      (println "Waiting for input ('o' to open a browser, '?' to see all shortcuts)")
      (loop []
        (let [c (char (.read reader))]
          (if-let [{:keys [fn name]} (commands c)]
            (do
              (println (ansi/compose [:yellow name]))
              (fn opts)
              (flush))
            (println "Unrecognized command:" (pr-str c)))
          (recur)))
      (finally
        (.setAttributes term attrs)
        (.close term)))))

; api

; TODO: this is the wrong interface, out-dir and build-dir should use flower.edn instead
(defn watch
  [& {:keys [port live-reload-port out-dir debounce-period]
      :or {out-dir "public"
           ; ms
           debounce-period 100}
      :as opts}]
  ; ninja might not have run yet; create an out dir anyway so we can watch it.
  (fs/create-dirs out-dir)
  (let [; prints out its own progress info
        port (find-port http-server/serve 8090 {:dir out-dir :port port})
        _ (do (print "Starting live reload watcher for" (str out-dir "/") "... ")
              (flush))
        reload-port (find-port live-reload 35729
                               {:dir out-dir :port live-reload-port :period debounce-period})
        ninja-opts (assoc opts :port port :live-reload-port reload-port)]
    (println "\rStarted live reload watcher for" (str out-dir "/")
             "on" (str "http://localhost:" reload-port))
    (println "Run `flower configure`")
    (binding [reflect/*watch-port* reload-port]
      (cmd/run-configure opts))
    ; Run this last since ninja emits its own output
    (println "Starting ninja watcher for `cd" *site* "&& ninja -t inputs`"
             "with debounce period" debounce-period)
    (watch-ninja ninja-opts debounce-period)
    ; Run once at startup. Block until it finishes.
    (rerun-ninja ninja-opts {})
    ; Watch for interactive input.
    (watch-input ninja-opts)))

