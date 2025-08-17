; Portions copyright Masashi Iizuka under Eclipse Public License 2.0
; see https://github.com/liquidz/frontmatter

(ns flower.frontmatter
  (:require 
    [clojure.string :as str]
    [clojure.data.json :as json]
    [clojure.edn       :as edn]
    [toml-clj.core :as toml]))
(binding [*warn-on-reflection* false]
  (require '[yaml.core     :as yaml]))

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
