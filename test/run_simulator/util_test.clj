(ns run-simulator.util-test
  (:require [clojure.java.io :as io]
            [run-simulator.util :as util]
            [clojure.test :as t]
            [clojure.spec.test.alpha :as stest]))

(t/deftest randomly-select-unit
  (t/testing "Empty collection returns nil"
    (t/is (nil? (util/randomly-select []))))
  (t/testing "One-element collection always returns same element."
    (t/is (= :a (util/randomly-select [:a]))))
  (t/testing "Selected element is member of input collection"
    (let [coll #{1 2 3 4 5}]
      (t/is (contains? coll (util/randomly-select (vec coll))))))
  )


(t/deftest int->well-unit
  (t/testing "0 => A01"
    (t/is (= "A01" (util/int->well 0))))
  (t/testing "95 => H12"
    (t/is (= "H12" (util/int->well 95))))
  (t/testing "96 => A01"
    (t/is (= "A01" (util/int->well 96))))
  )


(t/deftest now!-unit
  (t/testing "Timestamp matches regex"
    (t/is (not (nil? (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+([+-]\d{2}:\d{2}|Z)" (util/now!))))))
  )


(t/deftest weighted-random-select-unit
  (t/testing "Single-item map always returns that item"
    (t/is (= "A" (util/weighted-random-select {"A" 1.0}))))
  (t/testing "Result is always a key from the weight map"
    (let [weights {"X" 0.5 "Y" 0.3 "Z" 0.2}]
      (doseq [_ (range 50)]
        (t/is (contains? weights (util/weighted-random-select weights)))))))

          

