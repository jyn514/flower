(ns transformers.preprocess
  (:require [flower.reflect :as reflect]))

(defn run-preprocessor [pname {:keys [content filename locals]}]
  (case pname
    "sunflower" (reflect/preprocess-sunflower content filename locals)
    :else (throw (ex-info "unknown preprocessor" {:preprocessor pname}))))

(defn preprocess-file
  [content filename locals {:keys [preprocessors]}]
  (let [locals (if (string? locals) {'content locals} locals)]
    (if-not preprocessors
      (reflect/preprocess-sunflower content filename locals)
      (if-not (seq preprocessors)
        content
        (reduce run-preprocessor {:content content :filename filename :locals locals} preprocessors)))))

(defn transform [{:keys [content frontmatter]}]
  (let [{filename :flower/source-file} frontmatter]
    (preprocess-file content filename {} frontmatter)))
