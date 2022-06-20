(ns run-simulator.samplesheet-test
  (:require [run-simulator.samplesheet :as samplesheet]
            [clojure.test :as t]
            [clojure.spec.test.alpha :as stest]))


(t/deftest serialize-key-value-section-unit
  (t/testing "Empty input produces empty string"
    (t/is (= "" (samplesheet/serialize-key-value-section {} ""))))
  (t/testing "Empty data with header"
    (t/is (= "[Header]" (samplesheet/serialize-key-value-section {} "[Header]"))))
  (t/testing "Data and header"
    (t/is (= "[Header]\na,1\nb,2" (samplesheet/serialize-key-value-section {:a 1 :b 2} "[Header]")))))
  
