; Portions copyright Masashi Iizuka under Eclipse Public License 2.0
; see https://github.com/liquidz/frontmatter

(ns flower.frontmatter
  (:require
   [clj-yaml.core     :as yaml]
   [clojure.data.json :as json]
   [clojure.edn       :as edn]
   [clojure.string :as str]
   [clojure.walk :refer [postwalk]]
   [flower.internal.utils :refer [remove-ext remove-parent]]
   [toml-clj.core :as toml]) 
  (:import
   [java.time LocalDate LocalDateTime ZoneOffset]
   [java.util Date]))

; https://github.com/liquidz/frontmatter/blob/34a86ed3c6524f63cb457079c1316d9707be061a/src/frontmatter/core.clj
(defn- split-lines
  [lines delim]
  (let [x (take-while #(not= delim %) lines)]
    (list x (drop (+ 1 (count x)) lines))))

(defn- parse-yaml [s]
  (let [yaml (yaml/parse-string s)]
    (postwalk #(if-not (instance? java.util.Date %) %
                (-> % Date/.toInstant str))
              yaml)))

(defn- parse-toml [s]
  (let [toml (toml/read-string s {:key-fn keyword})]
    (postwalk #(if-not (instance? java.time.LocalDate %) %
                 (-> % LocalDate/.atStartOfDay (LocalDateTime/.toInstant ZoneOffset/UTC) str))
              toml)))

(defn- parse-json [s]
  (json/read-str (str "{" s "}")
                 :key-fn keyword))

(defn- parse-edn [s]
  (edn/read-string (str "{" s "}")))

(defn- select-parse-fn
  [first-line]
  (case first-line
    "---" parse-yaml
    "+++" parse-toml
    "===" parse-json ; TODO: just use {} like hugo
    ";;;" parse-edn
    nil))

(defn parse-frontmatter [filename content]
  (let [[first-line & rest-lines] (str/split-lines content)
        [raw body] (split-lines rest-lines first-line)]
    (if-let [parser (select-parse-fn first-line)]
      (try
        [(parser (str/join "\n" raw)) (str/join "\n" body)]
        (catch java.lang.Exception e
          (throw (ex-info (str "failed to parse frontmatter for " filename) {} e))))
      [{} content])))

; TODO: allow customizing :flower/path
(defn split-frontmatter
  [{:keys [filename content]}]
  (let [[frontmatter body] (parse-frontmatter filename content)
        base (-> filename remove-parent remove-ext)
        dst (str (remove-ext base) ".html")
        merged (assoc frontmatter :flower/source-file filename :flower/path dst)]
    {:content body :frontmatter merged}))
