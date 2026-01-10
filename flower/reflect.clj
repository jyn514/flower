; this is used in flower.eval. don't create a circular dependency.
(ns flower.reflect
  (:require
    [flower.utils :refer [graal? strip-suffix]]
    [flower.defaults :as defaults])
  (:import
   [java.io StringWriter]))

; NOTE: these helpers are exposed to all transformers,
; so they must record their file dependencies.

(def ^:dynamic *ninja* "not for public use; see write-ninja!" (java.io.StringWriter.))
(def ^:dynamic *watch-port*
  "Set to a TCP port number when `flower watch` is running."
  nil)
(def ^:dynamic *root*
  "Site URL root"
  "/")
(def ^:dynamic *metadata*
  "A {:settings {\"name\" string-or-bool} :pages {\"path\" frontmatter-map}} map"
  {})

(defn write-ninja! [str]
  (StringWriter/.write *ninja* ^String str))

; NOTE: ideally this would be an expression, but that means we have to generate
; a depfile for the transformer that's itself generating a depfile, which is a
; whole mess...just make it a built-in for now.
(def gen-depfile flower.utils/gen-depfile)

(def all-defaults defaults/all-default-paths)

(defn current-user []
  (System/getProperty "user.name"))

(defn current-exe []
  (if (graal?)
    (eval '(org.graalvm.nativeimage.ProcessProperties/getExecutableName))
    ; assumes we are in an uberjar file; this breaks horribly in scripts
    (-> clojure.lang.Atom .getProtectionDomain .getCodeSource .getLocation .getFile
        ; TODO: this is a horrible hack to make jyn.dev work nicely, but it's fine
        (strip-suffix ".jar"))))

; TODO: this sucks! i don't like having things only available in the guest :(
(declare preprocess-sunflower)
