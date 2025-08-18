; this is used in flower.eval. don't create a circular dependency.
(ns flower.reflect
  (:use flower.internal.utils)
  (:require
   [babashka.fs :as fs]) 
  (:import
   [java.io StringWriter]))

; NOTE: these helpers are exposed to all transformers,
; so they must record their file dependencies.

(def ^:dynamic *dependencies* "not for public use" #{})
(def ^:dynamic *ninja* "not for public use" "")
(def ^:dynamic *watching*
  "whether the site is being built with 'flower watch'"
  true) ; TODO update in main
(def ^:dynamic *frontmatter*
  "A {:templates Frontmatter  :pages Frontmatter} map,
  where Frontmatter is a {\"path\" metadata-map}"
  {}); flower.reflect/*frontmatter*
(def ^:dynamic *transformers*
  "a mapping from transformer file extension to how to run it.
  transformer runners must read {html, frontmatter} JSON on stdin
  and write the same to stdout. they should not read or write to the filesystem.
  doing so will cause build caching to break."
  {})

(defn read-file [path]
  (set! *dependencies* (conj *dependencies* path))
  (fs/read-all-bytes path))

(defn write-ninja! [str]
  (StringWriter/.write *ninja* ^String str))

; TODO: this sucks! i don't like having things only available in the guest :(
(declare render-file)
; (def render flower.eval/render)
; (reexport flower.eval/render)
