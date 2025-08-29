(ns parser
  (:require
   [clojure.string :as str]
   [clojure.core.match :refer [match]]
   [clojure.spec.alpha :as s]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [expectations.clojure.test :as expect :refer [defexpect expect]]
   [flower.eval :as eval]
   [instaparse.core :as insta]))

(def gen-sunflower-ident (gen/fmap pr-str gen/symbol))
(def gen-lisp
  ; like gen/simple-type, but without chars
  ; self-test breaks horribly on chars, just skip them for now
  (let [gen-scalar
        (gen/one-of
          [gen/small-integer gen/size-bounded-bigint gen/double gen/string
           gen/ratio gen/boolean gen/keyword gen/keyword-ns gen/symbol
           gen/symbol-ns gen/uuid])
        gen-collection (gen/recursive-gen gen/container-type gen-scalar)]
    (gen/fmap pr-str gen-collection)))

; (def gen-call
;   (gen/fmap (fn [[name args]]
;               (str "(" name " " args ")"))
;             (gen/tuple gen-sunflower-ident gen-lisp)))

(def gen-text
  ; https://github.com/Engelberg/instaparse/issues/241
  (gen/such-that #(not-any? #{\return} %) gen/string))

(def gen-comment
  (gen/fmap #(str ";" % "\n")
            (gen/such-that #(not-any? #{\newline} %) gen-text)))

(def gen-inline-render
  (let [not-brace (gen/such-that #(not-any? #{\»} %) gen/string-ascii)]
    (gen/fmap (fn [[name args body]]
                (str "(" name " "
                         args
                         ")«" body "»"))
              (gen/tuple gen-sunflower-ident gen-lisp not-brace))))

(def gen-sunflower-cmd
  (let [syntax (gen/one-of [gen-sunflower-ident gen-comment gen-lisp gen-inline-render])]
    (gen/fmap #(str "◊" %) syntax)))

(def gen-sunflower
  (gen/fmap str/join
            (gen/vector (gen/one-of [gen-text gen-sunflower-cmd]))))

(defn comment? [tree]
  (match tree
    [:Start [:FlowerSyntax [:OuterComment]]
            [:Text "\n"]] true
    :else false))

; (defn call? [tree]
;   (match tree
;     [:Start [:FlowerSyntax
;                     [:FlowerCall & _]]] true
;     :else false))

(defn inline-render? [tree]
  (match tree
    [:Start [:FlowerSyntax
             [:FlowerCall _
              [:CallTrailer [:NestedRender & _]]]]] true
    :else false))

(defspec self-test-parseable-comment 100
  (prop/for-all [s gen-comment]
    (let [all (str "◊" s)
          parsed (eval/parse all)]
      (expect comment? parsed (pr-str all)))))

; (defspec self-test-parseable-call 100
;   (prop/for-all [s gen-call]
;     (let [all (str "◊" s)
;           parsed (eval/parse all)]
;       (expect call? parsed (pr-str all)))))

(defspec self-test-parseable-render 100
  (prop/for-all [s gen-inline-render]
    (let [parsed (eval/parse (str "◊" s))]
      (expect inline-render? parsed (pr-str s)))))

(defn count-nodes [tree]
  (->> tree flatten (filter keyword?) frequencies))

(defn expect-nodes [src counts]
  (let [nodes (-> src eval/parse count-nodes
                  (select-keys (keys counts)))
        merged (merge (update-vals counts (constantly 0)) nodes)]
    (expect counts merged src)))

(defexpect edge-cases
  (expect-nodes "◊@a" {:OuterIdent 1 :ReaderAtom 1 :IdentNoBrackets 1
                       :Text 0})
  (expect-nodes "◊(a)(b)" {:FlowerCall 1 :Text 1})
  (expect-nodes "◊(a)◊(b)" {:FlowerCall 2 :Text 0})
  (expect-nodes "◊(a)«b»" {:FlowerCall 1 :NestedRender 1 :Text 0})
  (expect-nodes "◊(a)«b»«c»" {:FlowerCall 1 :NestedRender 2 :Text 0})
  (expect-nodes "◊(map #(+ 1 %) [])«b»" {:FlowerCall 1 :NestedRender 1 :Text 0})
  (expect-nodes "◊(->> xyz a)«b»" {:FlowerCall 1 :NestedRender 1 :Text 0})
  (expect-nodes "<a>◊xyz</a>" {:FlowerCall 0 :OuterIdent 1 :NestedRender 0
                               :Text 2})
  (expect-nodes "◊» ◊◊" {:OuterIdent 2 :Text 1})
  (expect-nodes "◊(str \n  ; TODO: xxx \n )" {:FlowerCall 1 :Text 0})
  (expect-nodes "◊; TODO: xxx" {:OuterComment 1 :Text 0})
  (expect-nodes "◊#_(a)«b»" {:ReaderSyntax 1 :FlowerCall 1 :NestedRender 1
                             :Text 0}))

; (s/def ::parse-result (s/coll-of (s/or :str string? :err insta/failure?)))
; (s/def ::transform-result
;   (s/coll-of (s/or :str string? :syn sequential? :err insta/failure?)))

; (defspec no-obvious-crashes 100
;   (prop/for-all [src gen-sunflower]
;     (let [filename "<test>"
;           tree (eval/parse src)
;           cx (eval/create-sci-cx filename)]
;     (expect ::transform-result (eval/transformer tree src cx)))))

(defn unambiguous? [parses]
  (< (count parses) 2))

(defspec unambiguous 100
  (prop/for-all [s gen-sunflower]
    (let [parsed (insta/parses eval/parse s)]
      (expect unambiguous? parsed))))

; TODO: test that all stack traces have at least one frame in user code
