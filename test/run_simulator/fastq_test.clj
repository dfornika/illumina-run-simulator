(ns run-simulator.fastq-test
  (:require [clojure.test :as t]
            [clojure.string :as str]
            [run-simulator.fastq :as fastq]))


(t/deftest format-fastq-header-unit
  (t/testing "Produces standard Illumina header"
    (let [header (fastq/format-fastq-header
                  {:instrument "M00123"
                   :run-number "0001"
                   :flowcell-id "000000000-ABC12"
                   :lane 1
                   :tile 1101
                   :x-pos 1234
                   :y-pos 5678
                   :read 1
                   :is-filtered "N"
                   :control-number 0
                   :index-sequence "ACGT+TGCA"})]
      (t/is (str/starts-with? header "@"))
      (t/is (str/includes? header "M00123"))
      (t/is (str/includes? header "ACGT+TGCA"))
      (t/is (= 2 (count (str/split header #" ")))))))


(t/deftest format-fastq-record-unit
  (t/testing "Produces 4-line record"
    (let [record (fastq/format-fastq-record "@header" "ACGT" "IIII")]
      (t/is (= 4 (count (str/split-lines record))))
      (t/is (str/starts-with? record "@header"))
      (t/is (str/includes? record "\n+\n")))))


(t/deftest generate-read-pair-unit
  (t/testing "Returns R1 and R2 records"
    (let [ref-seq (apply str (repeat 100 "ACGT"))
          result (fastq/generate-read-pair
                  {:reference-seq ref-seq
                   :read-length 50
                   :read-index 0
                   :instrument "M00123"
                   :run-number "0001"
                   :flowcell-id "FLOWCELL"
                   :lane 1
                   :tile 1101
                   :index-sequence "ACGT+TGCA"
                   :error-profile {:L 0.1 :k 0.05 :x0 200}})]
      (t/is (contains? result :r1-record))
      (t/is (contains? result :r2-record))
      (t/is (= 4 (count (str/split-lines (:r1-record result)))))
      (t/is (= 4 (count (str/split-lines (:r2-record result)))))))
  (t/testing "Read 1 header says read 1, read 2 header says read 2"
    (let [ref-seq (apply str (repeat 100 "ACGT"))
          result (fastq/generate-read-pair
                  {:reference-seq ref-seq
                   :read-length 50
                   :read-index 0
                   :instrument "M00123"
                   :run-number "0001"
                   :flowcell-id "FLOWCELL"
                   :lane 1
                   :tile 1101
                   :index-sequence "ACGT+TGCA"
                   :error-profile {:L 0.1 :k 0.05 :x0 200}})
          r1-header (first (str/split-lines (:r1-record result)))
          r2-header (first (str/split-lines (:r2-record result)))]
      (t/is (str/includes? r1-header " 1:"))
      (t/is (str/includes? r2-header " 2:")))))


(def ^:private base-fastq-params
  {:read-length 50
   :instrument "M00123"
   :run-number "0001"
   :flowcell-id "FLOWCELL"
   :lane 1
   :tile 1101
   :index-sequence "ACGT+TGCA"
   :error-profile {:L 0.1 :k 0.05 :x0 200}})


(t/deftest generate-sample-fastq-unit
  (t/testing "Generates content for R1 and R2"
    (let [ref-seq (apply str (repeat 100 "ACGT"))
          result (fastq/generate-sample-fastq
                  (assoc base-fastq-params
                         :primary-reference-seq ref-seq
                         :num-reads 5))]
      (t/is (string? (:r1-content result)))
      (t/is (string? (:r2-content result)))))
  (t/testing "Number of reads matches num-reads"
    (let [ref-seq (apply str (repeat 100 "ACGT"))
          num-reads 10
          result (fastq/generate-sample-fastq
                  (assoc base-fastq-params
                         :primary-reference-seq ref-seq
                         :num-reads num-reads))
          r1-headers (filter #(str/starts-with? % "@") (str/split-lines (:r1-content result)))]
      (t/is (= num-reads (count r1-headers)))))
  (t/testing "With cross-contamination, all reads are still valid"
    (let [primary-seq (apply str (repeat 100 "ACGT"))
          contaminant-seq (apply str (repeat 100 "TTTT"))
          result (fastq/generate-sample-fastq
                  (assoc base-fastq-params
                         :primary-reference-seq primary-seq
                         :contaminant-reference-seqs {"Contaminant" contaminant-seq}
                         :cross-contamination-rate 0.5
                         :num-reads 20))
          r1-headers (filter #(str/starts-with? % "@") (str/split-lines (:r1-content result)))]
      (t/is (= 20 (count r1-headers))))))
