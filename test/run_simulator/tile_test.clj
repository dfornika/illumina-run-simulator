(ns run-simulator.tile-test
  (:require [clojure.test :as t]
            [run-simulator.tile :as tile]))


(t/deftest generate-cluster-coords-unit
  (t/testing "Returns both float and integer coordinates"
    (let [coords (tile/generate-cluster-coords)]
      (t/is (contains? coords :x-float))
      (t/is (contains? coords :y-float))
      (t/is (contains? coords :x-pos))
      (t/is (contains? coords :y-pos))
      (t/is (number? (:x-float coords)))
      (t/is (number? (:y-float coords)))
      (t/is (integer? (:x-pos coords)))
      (t/is (integer? (:y-pos coords))))))


(t/deftest generate-cluster-unit
  (t/testing "Returns cluster with all expected keys"
    (let [ref-seq (apply str (repeat 100 "ACGT"))
          cluster (tile/generate-cluster
                   {:cluster-index 0
                    :sample-index 0
                    :sample-id "test-sample"
                    :index-sequence "ACGT+TGCA"
                    :reference-seq ref-seq
                    :read-length 50
                    :error-profile {:L 0.1 :k 0.05 :x0 200}})]
      (t/is (= 0 (:cluster-index cluster)))
      (t/is (= "test-sample" (:sample-id cluster)))
      (t/is (= 50 (count (:r1-bases cluster))))
      (t/is (= 50 (count (:r2-bases cluster))))
      (t/is (= 50 (count (:r1-quals cluster))))
      (t/is (= 50 (count (:r2-quals cluster))))
      (t/is (true? (:passed-filter cluster)))))
  (t/testing "Works with nil reference (random sequence)"
    (let [cluster (tile/generate-cluster
                   {:cluster-index 0
                    :sample-index 0
                    :sample-id "test-sample"
                    :index-sequence "ACGT+TGCA"
                    :reference-seq nil
                    :read-length 50
                    :error-profile {:L 0.1 :k 0.05 :x0 200}})]
      (t/is (= 50 (count (:r1-bases cluster))))
      (t/is (= 50 (count (:r2-bases cluster)))))))


(t/deftest generate-tile-clusters-unit
  (t/testing "Generates correct number of clusters across samples"
    (let [samples [{:Sample_ID "sample-1" :Index "ACGT" :Index2 "TGCA"
                    :_primary-species "SARS-CoV-2"
                    :_species-weights {"SARS-CoV-2" 1.0}
                    :_cross-contamination-rate 0}
                   {:Sample_ID "sample-2" :Index "GGGG" :Index2 "CCCC"
                    :_primary-species "SARS-CoV-2"
                    :_species-weights {"SARS-CoV-2" 1.0}
                    :_cross-contamination-rate 0}]
          ref-seq (apply str (repeat 100 "ACGT"))
          clusters (tile/generate-tile-clusters
                    {:samples samples
                     :fastq-params {:read-length 50
                                    :error-profile {:L 0.1 :k 0.05 :x0 200}
                                    :num-reads 5}
                     :reference-seqs {"SARS-CoV-2" ref-seq}})]
      (t/is (= 10 (count clusters)))
      (t/is (= 5 (count (filter #(= 0 (:sample-index %)) clusters))))
      (t/is (= 5 (count (filter #(= 1 (:sample-index %)) clusters)))))))
