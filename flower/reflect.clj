; this is used in flower.eval. don't create a circular dependency.
(ns flower.reflect
  (:use flower.utils)
  (:require [flower.defaults :as defaults])
  (:import
   [java.io StringWriter]))

; NOTE: these helpers are exposed to all transformers,
; so they must record their file dependencies.

(def ^:dynamic *dependencies* "not for public use" #{})
(def ^:dynamic *ninja* "not for public use" "")
(def ^:dynamic *watching*
  "whether the site is being built with 'flower watch'"
  true) ; TODO update in main
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

(defn current-exe []
  (if (graal?)
    (eval '(org.graalvm.nativeimage.ProcessProperties/getExecutableName))
    ; assumes we are in an uberjar file; this breaks horribly in scripts
    (-> clojure.lang.Atom .getProtectionDomain .getCodeSource .getLocation .getFile
        ; TODO: this is a horrible hack to make jyn.dev work nicely, but it's fine
        (strip-suffix ".jar"))))

; TODO: this sucks! i don't like having things only available in the guest :(
(declare render-file)
; (def render flower.eval/render)
; (reexport flower.eval/render)
