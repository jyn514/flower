; Portions copyright Masashi Iizuka under Eclipse Public License 2.0
; see https://github.com/liquidz/frontmatter

; NOTE: everything here must be possible to run in a sandbox with read-only access to the filesystem
; TODO: move this to expressions/

(ns flower.build
  (:import (java.io Writer))
  (:use [flower.internal.utils])
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.edn       :as edn]
            [toml-clj.core :as toml]))
(binding [*warn-on-reflection* false]
  (require '[yaml.core     :as yaml]))

; bound by 'configure
(def ^:dynamic *ninja* "not for public use" *err*)
(def ^:dynamic *frontmatter*
  "A {:templates Frontmatter  :pages Frontmatter} map,
  where Frontmatter is a {\"path\" metadata-map}"
  {})
(def ^:dynamic *transformers*
  "a mapping from transformer file extension to how to run it.
  transformer runners must read {html, frontmatter} JSON on stdin
  and write the same to stdout. they should not read or write to the filesystem.
  doing so will cause build caching to break."
  {})
(def ^:private nl "\n")

; fs utils

(defn remove-parent
  ([path] (remove-parent path 1))
  ([path n] (->> path fs/components (drop n) (apply fs/path))))

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
  [{:keys [filename content]}]
  (let [[first-line & rest-lines] (str/split-lines content)
        [frontmatter body]        (split-lines rest-lines first-line)]
    (if-let [parser (select-parse-fn first-line)]
      {:content (str/join "\n" body)
       :filename filename
       :frontmatter (parser (str/join "\n" frontmatter))}
      {:content content :filename filename :frontmatter {}})))

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
(defn join [xs]
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
     (when (contains? opts :description)
       (variable "description" (:description opts)))))

(defn- gen-build [opts]
  (let [out (join (:outputs opts))
        in (join (:inputs opts))
        implicit (join (:implicit opts))
        order (join (:order opts))]
    (apply str "build " out ": "
         (:rule opts) " " in
         (when (seq implicit) (str " | " implicit))
         (when (seq order) (str " || " order))
         nl
         (str/join
           (map-vars variable
                    (dissoc opts :outputs :inputs :implicit :order :rule)))
         )))

(defn register-transformer-runners [m]
  (alter-var-root #'*transformers* #(merge-deep % m)))

(defn generate
  ([ninja & late-bound]
   (let [all-maps (map #(% {:all-frontmatter *frontmatter*
                            :all-transformers *transformers*}) late-bound)
         merged (apply merge-deep ninja all-maps)]
     (generate merged)))
  ([ninja]
   (let [mapper (fn [[k v]]
                  (case k
                    :rules (conj (map gen-rule (filter some? v)) nl)
                    :builds (conj (map gen-build (filter some? v)) nl)
                    :variables (map-vars #(format "%s = %s\n" %1 %2) v)
                    ))
         contents (->> ninja (map mapper) flatten str/join)]
  (Writer/.write flower.build/*ninja* contents))))
