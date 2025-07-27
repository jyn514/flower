; (clojure.repl/dir build)
(def builddir ".build")
(def ninja
  {:rules
   [{:name "tmpdir"
     :command "mkdir -p ..build"
     :description "create build dir"}]
   :builds
   [{:rule "tmpdir"
     :outputs builddir}]})

(flower.build/generate ninja)
