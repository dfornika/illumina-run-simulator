(ns run-simulator.generators
  (:require [clojure.spec.gen.alpha :as gen]))

(def char-uppercase-alphanumeric
  (gen/such-that #(or (Character/isUpperCase %) (Character/isDigit %)) (gen/char-alphanumeric)))

(def char-uppercase-alphabetic
  (gen/such-that #(Character/isUpperCase %) (gen/char-alphanumeric)))


(def miseq-flowcell-id
  (gen/fmap #(apply str "000000000-" %) (gen/vector char-uppercase-alphanumeric 5)))

(def nextseq-flowcell-id
  (gen/fmap #(apply str "AAA" %) (gen/vector char-uppercase-alphanumeric 6)))


(defn make-zero-padded-num
  [min max digits]
  (let [zero-pad (fn [n] (format (str "%0" digits "d") n))]
    (gen/fmap zero-pad (gen/choose min max))))


(def zero-padded-month
  (let [pad-zero #(if (> % 9) (str %) (str 0 %))]
    (gen/fmap pad-zero (gen/choose 1 12))))


(def two-digit-year
  (gen/fmap str (gen/choose 16 32)))


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
    (gen/bind zero-padded-month (fn [month] (gen/fmap #(str month %) (make-zero-padded-num 1 (num-days month) 2))))))


(def six-digit-date
  (gen/bind two-digit-year
            (fn [year] (gen/fmap #(str year %) four-digit-date))))


(def miseq-instrument-id
  (gen/fmap #(str "M" %) (make-zero-padded-num 1 9999 5)))

(def nextseq-instrument-id
  (gen/fmap #(str "VH" %) (make-zero-padded-num 1 999 5)))


(def miseq-run-id
  (-> six-digit-date
      (gen/bind (fn [x] (gen/fmap #(str x "_" %) miseq-instrument-id)))
      (gen/bind (fn [x] (gen/fmap #(str x "_" %) (make-zero-padded-num 1 9999 4))))
      (gen/bind (fn [x] (gen/fmap #(str x "_" %) miseq-flowcell-id)))))

(def nextseq-run-id
  (-> six-digit-date
      (gen/bind (fn [x] (gen/fmap #(str x "_" %) nextseq-instrument-id)))
      (gen/bind (fn [x] (gen/fmap #(str x "_" (str %)) (gen/choose 1 500))))
      (gen/bind (fn [x] (gen/fmap #(str x "_" %) nextseq-flowcell-id)))))


(def library-id
  (-> (gen/return "BC")
      (gen/bind (fn [x] (gen/fmap #(str x %) two-digit-year)))
      (gen/bind (fn [x] (gen/fmap #(str x %) char-uppercase-alphabetic)))
      (gen/bind (fn [x] (gen/fmap #(str x (format "%03d" %)) (gen/choose 0 999))))
      (gen/bind (fn [x] (gen/fmap #(str x %) char-uppercase-alphabetic)))))

(def container-id
  (-> (gen/return "R")
      (gen/bind (fn [x] (gen/fmap #(str x (format "%010d" %)) (gen/choose 0 9999999999))))))

(comment
  (gen/sample miseq-run-id 10)
  (gen/sample miseq-flowcell-id 1)
  (gen/sample nextseq-run-id 10)
  (gen/sample library-id 10)
  (gen/sample container-id 1)
  )
