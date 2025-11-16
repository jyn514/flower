(ns build
  (:require
   [expressions.default-build :as builder :refer [add-ext]]
   [expressions.ninja :as ninja :refer [escape-ninja]]
   [expressions.utils :refer [escape-shell fmt inspect merge-deep write-edn]]
   [flower.fs :as fs]
   [flower.reflect :as reflect]))

(def settings (:settings reflect/*metadata*))
(def rebuild-flower (not= "false" (get settings "rebuild-flower")))
(def use-jar (= "jar" (get settings "rebuild-flower")))

(def flower-cli (reflect/current-exe))
(def build-cmd (if use-jar "uberjar" "native-dev"))

(def / fs/path)
(def public "public")
(def builddir ".build")

(def meta-build
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

;; (:tags [{:tags [:a :b :c] :name "x"} {:tags [:b] :name "y"}])
;; => {:a [{:name "x"}] :b [{:name "x"} {:name "y"}] :c [{:name "x"}]}
(defn flat-group-by [f pages]
  (apply merge-with into
         (for [page pages
               tag (f page)]
           {tag [page]})))

(def ^:private tags
  {:builds
   (for [[tag pages] (flat-group-by #(-> % :taxonomies :tags)
                                    (map second (:pages flower.reflect/*metadata*)))
         :let [path (add-ext tag "html")]]
     {:rule "tag"
      :tag tag
      :outputs (/ public "tags" path)
      :depfile (/ builddir "tags" (add-ext path "d"))
      :pages (-> (with-out-str (write-edn [tag pages])) escape-shell escape-ninja)})
   :rules
   [{:name "tag"
     :description "Synthesize a list of pages tagged '$tag'"
     :command (fmt "echo $pages | ${flower-cli} transform --standalone --raw-input --raw-output --tag $tag --depfile $depfile --out-file $out transformers/standalone/generate_tags.clj > $out")}]})

(->> (builder/default-build-plan) (merge-deep meta-build tags) ninja/generate reflect/write-ninja!)
