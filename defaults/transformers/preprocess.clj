(ns transformers.preprocess
  (:require [flower.reflect :as reflect]))

(defn run-preprocessor [{:keys [content filename] :as page} pname]
  (case pname
    "sunflower" (reflect/preprocess-sunflower content filename)
    "identity" page
    :else (throw (ex-info "unknown preprocessor" {:preprocessor pname}))))

(defn transform [{page :content meta :frontmatter :as input}]
  (let [{:keys [preprocessors] filename :flower/source-file} meta]
    (if-not preprocessors
      (reflect/preprocess-sunflower page filename)
      (reduce run-preprocessor input preprocessors))))
