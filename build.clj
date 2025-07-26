; (clojure.repl/dir build)
(build/rule
  {:name "tmpdir"
   :command "mkdir -p ..build"})
