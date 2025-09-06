(ns build
  (:require
   [babashka.fs :as fs]
   [expressions.default-build :as builder]
   [expressions.ninja :as ninja]
   [expressions.utils :refer [fmt merge-deep]]
   [flower.reflect :as reflect]))

(def settings (:settings reflect/*metadata*))
(def rebuild-flower (not= "false" (get settings "rebuild-flower")))
(def use-jar (= "jar" (get settings "rebuild-flower")))

(def ^:private defaults
  ; MANIFEST.txt gets rebuilt when we rebuild flower.
  ; avoid it always showing up as dirty.
  (remove #{(fs/path "../defaults/MANIFEST.txt")}
          (fs/glob "../defaults" "**")))

(def flower-cli (reflect/current-exe))
(def ff [flower-cli])

(def plan
  {:phony [{:name "flower" :depends ff}]
   :rules
   [{:name "flower-defaults"
     :restat true
     :command (fmt "cd ../defaults && ${flower-cli} configure")
     :description "rebuild default build.ninja"}
    {:name "flower-meta"
     :command (if use-jar "cd .. && clojure -T:build uberjar" "cd .. && clojure -T:build native-dev")
     :description "rebuild flower itself"}]
   :builds
   [(when rebuild-flower
      {:rule "flower-meta"
        :outputs ff
        :inputs (concat (fs/glob "../flower" "**") defaults ["../native.clj" "../flower" "../deps.edn"])})
    (when rebuild-flower
       {:rule "flower-defaults"
        :outputs "../defaults/build.ninja"
        :inputs "../defaults/build.clj"})]})

(def default-plan (builder/default-build-plan))

(ninja/generate! (merge-deep default-plan plan))
