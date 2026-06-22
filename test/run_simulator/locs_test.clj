(ns run-simulator.locs-test
  (:require [clojure.java.io :as io]
            [run-simulator.locs :as locs]
            [run-simulator.util :as util]
            [run-simulator.generators :as run-sim-gen]
            [clojure.test :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.spec.test.alpha :as stest]))

(t/deftest random-coord-unit
  (t/testing ""
    (let [get-x-y-floats (juxt :x-float :y-float)]
      (t/is (= [0.0 0.0] (get-x-y-floats (locs/random-coord {:min-x 0 :min-y 0 :max-x 0 :max-y 0})))))))


(defspec random-coord-within-bounds
  (prop/for-all [bounds run-sim-gen/xy-bounds]
                (let [coord (locs/random-coord bounds)
                      min-x (:min-x bounds)
                      min-y (:min-y bounds)
                      max-x (:max-x bounds)
                      max-y (:max-y bounds)]
                  (and (>= (:x-float coord) min-x)
                       (>= (:y-float coord) min-y)
                       (>= max-x (:x-float coord))
                       (>= max-y (:y-float coord))))))


(t/deftest num-clusters-unit
  (t/testing "Reads cluster count from picard test LOCS file"
    (let [locs-bytes (util/slurp-bytes "test/resources/picard_s_1_6.locs")]
      (t/is (pos? (locs/num-clusters locs-bytes))))))


(t/deftest write-locs-file-roundtrip
  (t/testing "Written LOCS file can be read back with correct cluster count and coordinates"
    (let [clusters [{:x-float 12.5 :y-float 34.7}
                    {:x-float 100.0 :y-float 200.0}
                    {:x-float 0.0 :y-float 0.0}]
          tmp-file (io/file "test_output" "locs_write_test" "test.locs")]
      (try
        (locs/write-locs-file! tmp-file clusters)
        (let [bytes (util/slurp-bytes (str tmp-file))
              n (locs/num-clusters bytes)
              coord-bytes (drop locs/locs-header-size bytes)
              coords (map locs/byteseq->coord (partition 8 coord-bytes))]
          (t/is (= 3 n))
          (t/is (= 3 (count coords)))
          (t/is (< (Math/abs (- 12.5 (:x-float (first coords)))) 0.01))
          (t/is (< (Math/abs (- 34.7 (:y-float (first coords)))) 0.01))
          (t/is (< (Math/abs (- 100.0 (:x-float (second coords)))) 0.01))
          (t/is (< (Math/abs (- 200.0 (:y-float (second coords)))) 0.01)))
        (finally
          (io/delete-file tmp-file true)
          (doseq [d [(io/file "test_output" "locs_write_test")
                     (io/file "test_output")]]
            (.delete d)))))))
