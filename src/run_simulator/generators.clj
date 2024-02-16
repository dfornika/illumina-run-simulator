(ns run-simulator.generators
  (:require [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as spec-gen]
            [clojure.spec.test.alpha :as spec-test]))


(def char-uppercase-alphanumeric
  (spec-gen/such-that #(or (Character/isUpperCase %) (Character/isDigit %)) (spec-gen/char-alphanumeric)))


(def char-uppercase-alphabetic
  (spec-gen/such-that #(Character/isUpperCase %) (spec-gen/char-alphanumeric)))


(def miseq-flowcell-id
  (spec-gen/fmap #(apply str "000000000-" %) (spec-gen/vector char-uppercase-alphanumeric 5)))


(def nextseq-flowcell-id
  (spec-gen/fmap #(apply str "AAA" %) (spec-gen/vector char-uppercase-alphanumeric 6)))


(defn make-zero-padded-num
  [min max digits]
  (let [zero-pad (fn [n] (format (str "%0" digits "d") n))]
    (spec-gen/fmap zero-pad (spec-gen/choose min max))))


(def zero-padded-month
  (let [pad-zero #(if (> % 9) (str %) (str 0 %))]
    (spec-gen/fmap pad-zero (spec-gen/choose 1 12))))


(def two-digit-year
  (spec-gen/fmap str (spec-gen/choose 16 32)))


(def four-digit-date
  (let [num-days {"01" 31
                  "02" 28
                  "03" 31
                  "04" 30
                  "05" 31
                  "06" 30
                  "07" 31
                  "08" 31
                  "09" 30
                  "10" 31
                  "11" 30
                  "12" 31}]
    (spec-gen/bind zero-padded-month (fn [month] (spec-gen/fmap #(str month %) (make-zero-padded-num 1 (num-days month) 2))))))


(def six-digit-date
  (spec-gen/bind two-digit-year
            (fn [year] (spec-gen/fmap #(str year %) four-digit-date))))


(def six-digit-timestamp
  (-> (make-zero-padded-num 0 24 2)
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x %) (make-zero-padded-num 0 60 2))))
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x %) (make-zero-padded-num 0 60 2))))))


(def miseq-instrument-id
  (spec-gen/fmap #(str "M" %) (make-zero-padded-num 1 9999 5)))


(def nextseq-instrument-id
  (spec-gen/fmap #(str "VH" %) (make-zero-padded-num 1 999 5)))


(def miseq-run-id
  (-> six-digit-date
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x "_" %) miseq-instrument-id)))
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x "_" %) (make-zero-padded-num 1 9999 4))))
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x "_" %) miseq-flowcell-id)))))


(def miseq-run-id-regex #"^[0-9]{6}_M[0-9]{5}_[0-9]{4}_000000000-[A-Z0-9]{5}$")


(spec/def ::miseq-run-id (spec/and string? #(re-matches miseq-run-id-regex %)))


(def nextseq-run-id
  (-> six-digit-date
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x "_" %) nextseq-instrument-id)))
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x "_" (str %)) (spec-gen/choose 1 500))))
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x "_" %) nextseq-flowcell-id)))))


(def nextseq-run-id-regex #"^[0-9]{6}_VH[0-9]{5}_[0-9]+_[A-Z0-9]{9}$")


(spec/def ::nextseq-run-id (spec/and string? #(re-matches miseq-run-id-regex %)))


(def library-id
  (-> (spec-gen/return "BC")
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x %) two-digit-year)))
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x %) char-uppercase-alphabetic)))
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x (format "%03d" %)) (spec-gen/choose 0 999))))
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x %) char-uppercase-alphabetic)))))


(def container-id
  (-> (spec-gen/return "R")
      (spec-gen/bind (fn [x] (spec-gen/fmap #(str x (format "%010d" %)) (spec-gen/choose 0 9999999999))))))


(defn ordered-int-pair
  "Create a pair of integers such that the second
   is larger than the first, and both are between min and max."
  [min max]
  (let [x (+ min (rand-int max))
        diff (- max x)
        y (+ x (rand-int diff))]
    [x y]))


(spec/fdef ordered-int-pair
  :args (spec/and (spec/cat :min int? :max int?)
                  #(>= (:min %) 0)
                  #(>= (:max %) 0)
                  #(< (:min %) (:max %)))
  :ret vector?
  :fn (spec/and #(>= (first (:ret %)) (-> % :args :min))
                #(<= (second (:ret %)) (-> % :args :max))
                #(>= (second (:ret %)) (first (:ret %)))))

(spec/def ::ordered-int-pair
  (spec/and
   (spec/cat :min pos-int? :max pos-int?)
   #(< (:min %) (:max %))))


(def xy-bounds
  (-> (spec-gen/return {})
      (spec-gen/bind (fn [x] (spec-gen/fmap #(assoc x :min-x (first %) :max-x (second %)) (spec/gen ::ordered-int-pair))))
      (spec-gen/bind (fn [x] (spec-gen/fmap #(assoc x :min-y (first %) :max-y (second %)) (spec/gen ::ordered-int-pair))))))


(comment
  (spec-gen/sample miseq-run-id 10)
  (spec-gen/sample miseq-flowcell-id 1)
  (spec-gen/sample nextseq-run-id 10)
  (spec-gen/sample library-id 10)
  (spec-gen/sample container-id 1)
  (spec-gen/sample xy-bounds 10)
  )
