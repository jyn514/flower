(ns transformers.preprocess
  (:require [flower.reflect :as reflect]))

(defn run-preprocessor [{:keys [content filename locals] :as page} pname]
  (case pname
    "sunflower" (reflect/preprocess-sunflower content filename locals)
    :else (throw (ex-info "unknown preprocessor" {:preprocessor pname}))))

; TODO: this API doesn't allow passing in custom preprocessors, which makes it useless :/
(def preprocess-file reflect/preprocess-sunflower)
#_(defn preprocess-file
  [source filename locals]
  (let [preprocessors "TODO???"]
    (run-preprocessor {:content source :filename filename :locals locals}) pname))

(defn transform [{page :content meta :frontmatter :as input}]
  (let [{:keys [preprocessors] filename :flower/source-file} meta]
    (if-not preprocessors
      (reflect/preprocess-sunflower page filename)
      (reduce run-preprocessor input preprocessors))))
