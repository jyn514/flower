; this is used in flower.eval. don't create a circular dependency.
(ns flower.reflect
  (:use flower.internal.utils)
  (:require [babashka.fs :as fs]))

; NOTE: these helpers are exposed to all transformers,
; so they must record their file dependencies.

(def ^:dynamic *dependencies* "not for public use" #{})
(def ^:dynamic *watching*
  "whether the site is being built with 'flower watch'"
  true) ; TODO update in main

(defn read-file [path]
  (set! *dependencies* (conj *dependencies* path))
  (fs/read-all-bytes path))

; TODO: this sucks! i don't like having things only available in the guest :(
(declare render-file)
; (def render flower.eval/render)
; (reexport flower.eval/render)
