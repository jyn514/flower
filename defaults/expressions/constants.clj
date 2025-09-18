(ns expressions.constants 
  (:require
   [flower.reflect :as reflect]))

(def site-title "my flower site")
(def site-author (reflect/current-user))
(def global-desc site-title)
(def lang "en")
