(ns run-simulator.reference-test
  (:require [clojure.test :as t]
            [run-simulator.reference :as reference]))


(t/deftest load-registry-unit
  (t/testing "Registry loads and contains expected entries"
    (let [registry (reference/load-registry!)]
      (t/is (vector? registry))
      (t/is (pos? (count registry)))
      (t/is (every? :species registry))
      (t/is (every? :fasta registry))
      (t/is (every? :id registry)))))


(t/deftest load-references-unit
  (t/testing "Loads reference sequences keyed by species"
    (let [refs (reference/load-references!)]
      (t/is (map? refs))
      (t/is (pos? (count refs)))
      (t/is (every? string? (keys refs)))
      (t/is (every? string? (vals refs)))
      (t/is (every? #(pos? (count %)) (vals refs))))))
