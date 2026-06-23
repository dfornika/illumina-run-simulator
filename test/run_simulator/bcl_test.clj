(ns run-simulator.bcl-test
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
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


(t/deftest num-clusters-unit
  (t/testing "Reads cluster count from picard test BCL file"
    (let [bcl-bytes (util/slurp-bytes "test/resources/picard_bcl_passing.bcl")]
      (t/is (pos? (bcl/num-clusters bcl-bytes))))))


(t/deftest encode-bcl-byte-unit
  (t/testing "Roundtrip: encode then decode recovers base and quality"
    (doseq [base [\A \C \G \T]
            qual [2 10 30 41 63]]
      (let [encoded (bcl/encode-bcl-byte base qual)
            decoded (bcl/byte->basecall encoded)]
        (t/is (= base (:base decoded)))
        (t/is (= qual (:qual-int decoded))))))
  (t/testing "A with quality 0 produces null byte (no-call sentinel)"
    (t/is (= 0 (Byte/toUnsignedInt (bcl/encode-bcl-byte \A 0)))))
  (t/testing "N base encodes as null byte"
    (t/is (= 0 (Byte/toUnsignedInt (bcl/encode-bcl-byte \N 30))))))


(t/deftest write-bcl-file-unit
  (t/testing "Written BCL file has correct header and data"
    (let [clusters [{:r1-bases "ACG" :r1-quals [30 20 10]
                     :r2-bases "TGA" :r2-quals [25 15 5]}
                    {:r1-bases "TTT" :r1-quals [40 35 30]
                     :r2-bases "AAA" :r2-quals [38 33 28]}]
          tmp-file (io/file "test_output" "bcl_write_test" "test.bcl")]
      (try
        (bcl/write-bcl-file! tmp-file clusters :r1-bases :r1-quals 0)
        (let [bytes (util/slurp-bytes (str tmp-file))
              n (bcl/num-clusters bytes)
              data-bytes (drop 4 bytes)]
          (t/is (= 2 n))
          (t/is (= \A (:base (bcl/byte->basecall (first data-bytes)))))
          (t/is (= 30 (:qual-int (bcl/byte->basecall (first data-bytes)))))
          (t/is (= \T (:base (bcl/byte->basecall (second data-bytes)))))
          (t/is (= 40 (:qual-int (bcl/byte->basecall (second data-bytes))))))
        (finally
          (io/delete-file tmp-file true)
          (doseq [d [(io/file "test_output" "bcl_write_test")
                     (io/file "test_output")]]
            (.delete d)))))))
