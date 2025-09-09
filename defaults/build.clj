(ns build
  (:require
   [expressions.default-build :as builder]
   [expressions.ninja :as ninja]
   [flower.reflect :as reflect]))
(reflect/write-ninja! (ninja/generate (builder/default-build-plan)))
