; HACK: this file is required from native.clj itself, so it can't use any flower namespaces, not even recursively through other `expressions`.
(ns expressions.ninja
  (:require
   [clojure.string :as str]))

(def ^:private nl "\n")

; ninja utils

(defn- variable [key val] (format "  %s = %s\n" key val))

(defn escape-ninja
  "Escape a string for use as a ninja file path.
   See https://ninja-build.org/manual.html#ref_lexer"
  [s]
  ; https://github.com/ninja-build/ninja/blob/370edd49a47379d0c3ff0c0ae9d825e627fd37c3/misc/ninja_syntax.py#L30
  (-> s str
      ; NOTE: $ has to come first
      (str/replace "$" "$$")
      (str/replace "\n" "$\n")
      (str/replace " " "$ ")
      (str/replace ":" "$:")))

(defn- format-ninja
  "Given a string, escape it for use as a ninja path.
   Given a keyword, treat it as a ninja variable."
  [spec]
  (if (keyword? spec)
     (str "${" (name spec) "}")
     (escape-ninja spec)))

(defn- join-ninja
  "Given a list of file paths, format them as a ninja dependency set."
  [xs]
  (let [xs (if (or (nil? xs) (sequential? xs))
             xs [xs])]
    (->> xs (map format-ninja) (str/join " "))))

(defn- map-vars
  [f m]
  (map
    (fn [[k v]]
      (let [n (name k)]
        (f n v))) m))

; ninja generators

(defn- gen-rule [opts]
  (str "rule " (:name opts) nl
       (str/join
         ; NOTE: ninja does not accept custom variables here, only built-in variables.
         ; To add a custom variable, use a top-level `:variable`.
         ; To see a list of built-ins, go to https://ninja-build.org/manual.html#ref_rule
         (map-vars variable
                   (dissoc opts :name)))))

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
           (join-ninja deps) nl)))

(defn- gen-var [[k v]]
  (format "%s = %s\n" (name k) (join-ninja v)))

; public API

; NOTE: variables are resolved lexically so they have to be generated first.
(defn generate
  ([ninja]
   (let [gen-all #(concat (map %1 (filter some? %2)) [nl])
         vars (gen-all gen-var (:variables ninja))
         phony (gen-all gen-phony (:phony ninja))
         rules (gen-all gen-rule (:rules ninja))
         builds (gen-all gen-build (:builds ninja))
         contents (str/join (concat vars phony rules builds))]
     contents)))
