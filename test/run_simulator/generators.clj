(ns run-simulator.generators
  (:require [clojure.test.check.generators :as gen]))

(def ordered-int-pair
  (gen/let [lower-bound gen/nat
            upper-bound (gen/such-that #(> % lower-bound) (gen/scale #(* % 100) gen/nat))]
    [lower-bound upper-bound]))

(def xy-bounds
  (gen/let [x-bounds ordered-int-pair
            y-bounds ordered-int-pair]
    {:min-x (first x-bounds)
     :min-y (first y-bounds)
     :max-x (second x-bounds)
     :max-y (second y-bounds)}))

(comment
  (gen/sample xy-bounds)
  )
  
