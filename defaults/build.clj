(ns build
  (:require [expressions.ninja :as ninja]
            [expressions.default-build :as builder]))
(ninja/generate! (builder/default-build-plan))
