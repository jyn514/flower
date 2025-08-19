(ns expressions.ninja
  (:use [flower.utils])
  (:require [clojure.string :as str]))

; bound by 'configure
; (def ^:dynamic *ninja* "not for public use" *err*)
; (def ^:dynamic *frontmatter*
;   "A {:templates Frontmatter  :pages Frontmatter} map,
;   where Frontmatter is a {\"path\" metadata-map}"
;   {}); flower.reflect/*frontmatter*
; (def ^:dynamic *transformers*
;   "a mapping from transformer file extension to how to run it.
;   transformer runners must read {html, frontmatter} JSON on stdin
;   and write the same to stdout. they should not read or write to the filesystem.
;   doing so will cause build caching to break."
;   {})

(def ^:private nl "\n")

; ninja utils

(defn- variable [key val] (fmt "  ${key} = ${val}\n"))

(defn- map-vars
  [f m]
  (map
    (fn [[k v]]
      (let [n (name k)]
        (f n v))) m))

; ninja generators

(defn- gen-rule [opts]
  (str "rule " (:name opts) nl
     ; TODO: replace all this with map-vars
       (variable "command" (:command opts))
     (when (contains? opts :description)
       (variable "description" (:description opts)))))

(defn- gen-build [opts]
  (let [out (join-ninja (:outputs opts))
        in (join-ninja (:inputs opts))
        implicit (join-ninja (:implicit opts))
        order (join-ninja (:order opts))]
    (apply str "build " out ": "
         (:rule opts) " " in
         (when (seq implicit) (str " | " implicit))
         (when (seq order) (str " || " order))
         nl
         (str/join
           (map-vars variable
                    (dissoc opts :outputs :inputs :implicit :order :rule)))
         )))

(defn- gen-phony [opts]
  (let [name (:name opts)
        deps (:depends opts)]
    (apply str "build " name ": phony "
           (join-ninja deps))))

(defn- mapper
  [[k v]]
  (case k
    :rules (concat (map gen-rule (filter some? v)) [nl])
    :builds (concat (map gen-build (filter some? v)) [nl])
    :phony (concat (map gen-phony (filter some? v)) [nl])
    :variables (map-vars #(format "%s = %s\n" %1 %2) v)
    :transformers []))

(defn generate
  ([ninja]
   (let [map-with-nl #(concat (mapper %) [nl])
         contents (->> ninja (map map-with-nl) flatten str/join)]
     (flower.reflect/write-ninja! contents))))
