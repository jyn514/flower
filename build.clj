; (clojure.repl/dir build)
(def builddir ".build")
(def ninja
  {:rules
     [{:name "tmpdir"
       :command "mkdir -p ..build"}]
   :builds
     [{:rule "tmpdir"
       :command (fmt "mkdir -p ${builddir}")
       :description "create build dir"}]})

(flower.build/generate ninja)
