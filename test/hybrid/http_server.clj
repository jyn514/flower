(ns test.hybrid.http-server 
  (:require
   [babashka.fs :as fs]
   [expectations.clojure.test :refer [defexpect expect]]
   [flower.watch :refer [static-server]]
   [org.httpkit.client :as http]))

(defn assert-http [url f]
  (f @(http/get url)))

(defexpect not-found
  (let [dir (fs/create-temp-dir {:prefix "flower-hybrid-test-"})
        port (+ 10000 (rand-int 10000))
        resolved-port (static-server port {:dir dir})
        custom-404 "<html><body>not found</body></html>"
        url (format "http://localhost:%d/not-a-file" resolved-port)]

    (assert-http url
                 #(do (expect 404 (:status %))
                      (expect "404 not found" (:body %))))

    (spit (str (fs/path dir "404.html")) custom-404)
    (assert-http url
                 #(do (expect 404 (:status %))
                      (expect "text/html" (->> % :headers :content-type))
                      (expect custom-404 (:body %))))))
