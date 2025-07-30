; NOTE: everything here must be possible to run in a sandbox with read-only access to the filesystem

(ns flower.build
  (:import (java.io Writer))
  (:use [flower.utils])
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.edn       :as edn]
            [yaml.core     :as yaml]
            [toml-clj.core :as toml]
            ))

; bound by 'configure
(def ^:dynamic *ninja* "not for public use" *err*)
(def ^:dynamic *frontmatter* "not for public use" [])
(def ^:dynamic *postprocessors*
  "a mapping from postprocessor file extension to how to run it.
  postprocessor runners must read {html, frontmatter} JSON on stdin
  and write the same to stdout. they should not read or write to the filesystem.
  doing so will cause build caching to break."
  {})
(def ^:private nl "\n")

; frontmatter

; https://github.com/liquidz/frontmatter/blob/34a86ed3c6524f63cb457079c1316d9707be061a/src/frontmatter/core.clj
(defn- split-lines
  [lines delim]
  (let [x (take-while #(not= delim %) lines)]
    (list x (drop (+ 1 (count x)) lines))))

(defn- parse-json [s]
  (json/read-str (str "{" s "}")
                 :key-fn keyword))

(defn- parse-edn [s]
  (edn/read-string (str "{" s "}")))

(defn- select-parse-fn
  [first-line]
  (case first-line
    "---" yaml/parse-string
    "+++" toml/read-string
    ";;;" parse-json ; TODO: just use {} like hugo
    "###" parse-edn
    nil))

; NOTE: maps use strings as keys, not keywords
(defn split-frontmatter
  [original-body]
  (let [[first-line & rest-lines] (str/split-lines original-body)
        [frontmatter body]        (split-lines rest-lines first-line)]
    (if-let [parser (select-parse-fn first-line)]
      {:content (str/join "\n" body)
       :frontmatter (parser (str/join "\n" frontmatter))}
      {:frontmatter {} :content original-body})))

(defn all-frontmatter
  "Returns a {:templates Frontmatter  :pages Frontmatter} map,
  where Frontmatter is a {\"path\" metadata-map}"
  [] *frontmatter*)

; ninja utils

(defn- escape
  "Escape a string for use as a ninja file path.
   See https://ninja-build.org/manual.html#ref_lexer"
  [s] 
  (-> s str
      (str/replace "\n" "$n")
      (str/replace " " "$ ")
      (str/replace ":" "$:")
      (str/replace "$" "$$")))

(defn- variable [key val] (fmt "  ${key} = ${val}\n"))
(defn- join [xs]
  (let [xs (if (or (nil? xs) (sequential? xs))
             xs
             [xs])]
    (->> xs (map escape) (str/join " "))))

(defn- map-vars
  [f m]
  (map
    (fn [[k v]]
      (let [n (name k)]
        (f n v))) m))

; ninja generators

(defn- gen-rule [opts]
  (str "rule " (:name opts) nl
       (variable "command" (:command opts))
     (if (contains? opts :description)
       (variable "description" (:description opts)))))

; TODO: support variables
; `(apply dissoc)` probably gets halfway there
(defn- gen-build [opts]
  (let [out (join (:outputs opts))
        in (join (:inputs opts))
        implicit (join (:implicit opts))
        order (join (:order opts))]
    (apply str "build " out ": "
         (:rule opts) " " in
         (if (seq implicit) (str " | " implicit))
         (if (seq order) (str " || " order))
         nl
         (str/join
           (map-vars variable
                    (dissoc opts :outputs :inputs :implicit :order :rule)))
         )))

(defn register-postprocessor-runners [m]
  (alter-var-root #'*postprocessors* #(merge-deep % m)))

(defn generate
  ([ninja & late-bound]
   (let [all-maps (map #(% {:all-frontmatter *frontmatter*
                            :all-postprocessors *postprocessors*}) late-bound)
         ; TODO: breaks when all-maps has more than one arg ??
         merged (apply merge-deep ninja all-maps)]
     (generate merged)))
  ([ninja]
   (let [mapper (fn [[k v]]
                  (case k
                    :rules (conj (map gen-rule v) nl)
                    :builds (conj (map gen-build v) nl)
                    :variables (map-vars #(format "%s = %s\n" %1 %2) v)
                    ))
         contents (->> ninja (map mapper) flatten str/join)]
  (Writer/.write flower.build/*ninja* contents))))
