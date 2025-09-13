(ns build
  (:require
   [expressions.default-build :as builder]
   [expressions.ninja :as ninja]
   [expressions.utils :refer [merge-deep]]
   [flower.fs :as fs]
   [flower.reflect :as reflect]))

(def settings (:settings reflect/*metadata*))
(def rebuild-flower (not= "false" (get settings "rebuild-flower")))
(def use-jar (= "jar" (get settings "rebuild-flower")))

(def flower-cli (reflect/current-exe))
(def build-cmd (if use-jar "uberjar" "native-dev"))

(def plan
  {:phony [{:name "flower" :depends flower-cli}]
   :rules
   [{:name "flower-ninja"
     :command (str "cd ../ && clojure -T:build gen-plan :build-cmd " build-cmd)
     :description "rebuild the meta-build system"}
    {:name "flower-bin"
     :command "cd .. && ninja"
     :description "rebuild flower"}]
   :builds
   [(when rebuild-flower
      {:rule "flower-ninja"
       :outputs "../build.ninja"
       :inputs "../native.clj"})
    (when rebuild-flower
      {:rule "flower-bin"
       :inputs (fs/glob "../flower" "**")
       :outputs flower-cli
       :order "../build.ninja"})]})

(def default-plan (builder/default-build-plan))
(ninja/generate! (merge-deep default-plan plan))
