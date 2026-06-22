(ns run-simulator.bcl-test
  (:require [clojure.test :as t]
            [run-simulator.bcl :as bcl]
            [run-simulator.util :as util]))


(t/deftest byte->base-unit
  (t/testing "Two low bits map to correct bases"
    (t/is (= \A (bcl/byte->base 0)))
    (t/is (= \C (bcl/byte->base 1)))
    (t/is (= \G (bcl/byte->base 2)))
    (t/is (= \T (bcl/byte->base 3))))
  (t/testing "Upper bits are ignored"
    (t/is (= \A (bcl/byte->base 0xFC)))
    (t/is (= \C (bcl/byte->base 0xFD)))
    (t/is (= \G (bcl/byte->base 0xFE)))
    (t/is (= \T (bcl/byte->base 0xFF)))))


(t/deftest byte->qual-unit
  (t/testing "Quality is upper 6 bits"
    (t/is (= 0 (bcl/byte->qual 0x00)))
    (t/is (= 0 (bcl/byte->qual 0x03)))
    (t/is (= 1 (bcl/byte->qual 0x04)))
    (t/is (= 63 (bcl/byte->qual 0xFF)))))


(t/deftest qual->char-unit
  (t/testing "PHRED+33 encoding"
    (t/is (= \! (bcl/qual->char 0)))
    (t/is (= \I (bcl/qual->char 40)))))


(t/deftest byte->basecall-unit
  (t/testing "Null byte returns N with quality 2"
    (t/is (= {:base \N :qual-int 2} (bcl/byte->basecall 0x00))))
  (t/testing "Non-null byte decodes base and quality"
    (let [result (bcl/byte->basecall 0xFC)]
      (t/is (= \A (:base result)))
      (t/is (= 63 (:qual-int result)))))
  (t/testing "Byte with C base and quality 10"
    (let [b (unchecked-byte (bit-or (bit-shift-left 10 2) 1))
          result (bcl/byte->basecall b)]
      (t/is (= \C (:base result)))
      (t/is (= 10 (:qual-int result))))))


;; bcl/num-clusters and locs/num-clusters pass 4 bytes to bytes->long
;; which expects 8 — a latent bug to fix separately.

