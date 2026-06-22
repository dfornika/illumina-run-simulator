(ns run-simulator.fasta-test
  (:require [clojure.test :as t]
            [run-simulator.fasta :as fasta]))


(t/deftest parse-fasta-single-seq
  (t/testing "Parses a single-sequence FASTA file"
    (let [records (fasta/parse-fasta "test/resources/NC_045512.2.head.fasta")]
      (t/is (= 1 (count records)))
      (t/is (= "NC_045512.2" (:id (first records))))
      (t/is (string? (:sequence (first records))))
      (t/is (pos? (count (:sequence (first records))))))))


(t/deftest parse-fasta-multi-seq
  (t/testing "Parses a multi-sequence FASTA file"
    (let [records (fasta/parse-fasta "test/resources/multiseqs.fasta")]
      (t/is (> (count records) 1))
      (t/is (every? :id records))
      (t/is (every? :sequence records)))))


(t/deftest parse-fasta-ids-are-correct
  (t/testing "IDs are first whitespace-delimited token after >"
    (let [records (fasta/parse-fasta "test/resources/multiseqs.fasta")]
      (t/is (every? #(not (.contains (:id %) " ")) records)))))
