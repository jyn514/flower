;; NOTE: we don't use (in-ns) to avoid having to run fixtures before each test
(ns flower.fs
  (:require
   babashka.fs))
(doseq [[sym var] (ns-publics 'babashka.fs)]
  (intern 'flower.fs sym var))
(load-file "flower/fs.clj")
