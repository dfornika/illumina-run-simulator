(ns run-simulator.util-test
  (:require [clojure.java.io :as io]
            [run-simulator.util :as util]
            [clojure.test :as t]
            [clojure.spec.test.alpha :as stest]))

(t/deftest randomly-select-unit
  (t/testing "Selected element is member of input collection"
    (t/is (contains? #{1 2 3 4 5} (util/randomly-select [1 2 3 4 5]))))
  )
