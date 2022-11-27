(ns run-simulator.locs-test
  (:require [clojure.java.io :as io]
            [run-simulator.locs :as locs]
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


(comment
  (gen/sample (gen/let [min-y gen/nat]
                (gen/such-that #(> % min-y) (gen/scale #(* % 10) gen/nat))))
  )
