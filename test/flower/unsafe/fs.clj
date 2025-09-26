(ns flower.unsafe.fs
  (:require babashka.fs))

(doseq [[sym var] (ns-publics 'babashka.fs)]
  (intern 'flower.unsafe.fs sym var))
