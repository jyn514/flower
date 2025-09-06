(ns transformers.highlight
  (:use expressions.meta expressions.utils expressions.html)
  (:require [clojure.string :as str]
            [flower.unsafe]))

; https://pygments.org/docs/formatters/#HtmlFormatter
(defn highlight
  [lang content]
  (let [handle (flower.unsafe/system
                 {:in content
                  :out :string}
                 "pygmentize -f html -O nowrap=1 -l" lang)]
    (:out @handle)))

(defn get-lang [code]
  (let [classes (some-> code attrs :class (str/split #"\s"))
        lang-cs (filter #(str/starts-with? % "language-") classes)
        lang (some-> lang-cs first (strip-prefix "language-"))]
    lang))

(defn transform [{page :content meta :frontmatter :as args}]
  (let [doc (->element page)]
    (doseq [code (select doc "code")
            :let [lang (get-lang code)]
            :when lang]
      (try (let [highlighted (highlight lang (text-content code))]
             (add-class! code "highlight")
             (replace-children! code highlighted))
           (catch clojure.lang.ExceptionInfo e
             (if (-> e ex-data :type (= :babashka.process/error))
               (println (str "warning: failed to highlight language " lang))
               (throw e)))))
    (flower.unsafe/register-dependencies!) ; no dependencies
    (str doc)))
