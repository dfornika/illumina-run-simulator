(ns run-simulator.generators
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]))


(def char-uppercase-alphanumeric
  (gen/elements (into (mapv char (range 65 91))
                      (mapv char (range 48 58)))))


(def char-uppercase-alphabetic
  (gen/elements (mapv char (range 65 91))))


(s/def ::i100-flowcell-num (s/int-in 2200000 2300000))
(def gen-i100-flowcell-num
  (gen/choose 2200000 2300000))


(def miseq-flowcell-id
  (gen/fmap #(apply str "000000000-" %) (gen/vector char-uppercase-alphanumeric 5)))


(def nextseq-flowcell-id
  (gen/fmap #(apply str "AAA" %) (gen/vector char-uppercase-alphanumeric 6)))

(def i100-flowcell-id
  (gen/fmap #(str "ASC" % "-SC3") gen-i100-flowcell-num))

(defn make-zero-padded-num
  [min max digits]
  (let [zero-pad (fn [n] (format (str "%0" digits "d") n))]
    (gen/fmap zero-pad (gen/choose min max))))


(def zero-padded-month
  (make-zero-padded-num 1 12 2))

(def two-digit-year
  (gen/fmap str (gen/choose 16 32)))

(def four-digit-year
  (gen/fmap str (gen/choose 2016 2032)))


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
    (gen/bind zero-padded-month
              (fn [month]
                (gen/fmap #(str month %) (make-zero-padded-num 1 (num-days month) 2))))))


(def six-digit-date
  (gen/fmap (fn [[year date]] (str year date))
            (gen/tuple two-digit-year four-digit-date)))

(def eight-digit-date
  (gen/fmap (fn [[year date]] (str year date))
            (gen/tuple four-digit-year four-digit-date)))


(def six-digit-timestamp
  (gen/fmap (fn [[h m s]] (str h m s))
            (gen/tuple (make-zero-padded-num 0 23 2)
                       (make-zero-padded-num 0 59 2)
                       (make-zero-padded-num 0 59 2))))


(def miseq-instrument-id
  (gen/fmap #(str "M" %) (make-zero-padded-num 1 9999 5)))


(def nextseq-instrument-id
  (gen/fmap #(str "VH" %) (make-zero-padded-num 1 999 5)))

(def i100-instrument-id
  (gen/fmap #(str "SH" %) (make-zero-padded-num 1 999 5)))


(def miseq-run-id
  (gen/fmap (fn [[date inst run fc]]
              (str/join "_" [date inst run fc]))
            (gen/tuple six-digit-date
                       miseq-instrument-id
                       (make-zero-padded-num 1 9999 4)
                       miseq-flowcell-id)))


(def miseq-run-id-regex #"^[0-9]{6}_M[0-9]{5}_[0-9]{4}_000000000-[A-Z0-9]{5}$")


(s/def ::miseq-run-id
  (s/with-gen
    (s/and string? #(re-matches miseq-run-id-regex %))
    (fn [] miseq-run-id)))


(def nextseq-run-id
  (gen/fmap (fn [[date inst run fc]]
              (str/join "_" [date inst run fc]))
            (gen/tuple six-digit-date
                       nextseq-instrument-id
                       (gen/fmap str (gen/choose 1 500))
                       nextseq-flowcell-id)))


(def nextseq-run-id-regex #"^[0-9]{6}_VH[0-9]{5}_[0-9]+_[A-Z0-9]{9}$")


(s/def ::nextseq-run-id
  (s/with-gen
    (s/and string? #(re-matches nextseq-run-id-regex %))
    (fn [] nextseq-run-id)))

(def i100-run-id
  (gen/fmap (fn [[date inst run fc]]
              (str/join "_" [date inst run fc]))
            (gen/tuple eight-digit-date
                       i100-instrument-id
                       (make-zero-padded-num 1 500 4)
                       i100-flowcell-id)))


(def i100-run-id-regex #"^[0-9]{8}_SH[0-9]{5}_[0-9]{4}_ASC[0-9]{7}-SC3$")

(s/def ::i100-run-id
  (s/with-gen
    (s/and string? #(re-matches i100-run-id-regex %))
    (fn [] i100-run-id)))

(def library-id
  (gen/fmap (fn [[year letter1 num letter2]]
              (str "BC" year letter1 (format "%03d" num) letter2))
            (gen/tuple two-digit-year
                       char-uppercase-alphabetic
                       (gen/choose 0 999)
                       char-uppercase-alphabetic)))


(def container-id
  (gen/fmap #(str "R" (format "%010d" %)) (gen/choose 0 9999999999)))


(comment
  (gen/sample miseq-run-id 10)
  (gen/sample miseq-flowcell-id 1)
  (gen/sample nextseq-run-id 10)
  (gen/sample i100-run-id 10)
  (gen/sample library-id 10)
  (gen/sample container-id 1)
  )
