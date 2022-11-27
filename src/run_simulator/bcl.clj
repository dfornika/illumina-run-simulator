(ns run-simulator.bcl
  (:require [run-simulator.util :as util])
  (:import java.nio.ByteBuffer))

(def bcl-header-size 4)

(defn byte->base
  ""
  [b]
  (let [base-num (bit-and 2r00000011 b)
        base-map {0 \A 1 \C 2 \G 3 \T}]
    (base-map base-num)))

(defn qual->char
  ""
  [q]
  (char (+ 33 q)))


(defn byte->qual
  ""
  [b]
  (bit-shift-right (bit-and 0xff b) 2))


(defn byte->basecall
  ""
  [b]
  (if (= 0x00 b)
    {:base \N :qual-int 2}
    {:base (byte->base b)
     :qual-int (byte->qual b)}))


(defn num-clusters
  ""
  [bcl-bytes]
  (let [num-clusters-bytes (take 4 bcl-bytes)]
    (util/bytes->int (byte-array num-clusters-bytes) :little-endian)))

(comment
    (def test-bcl-path "test/resources/picard_bcl_passing.bcl")
    (def test-bcl-bytes (util/slurp-bytes test-bcl-path))
    (def num-test-bcl-clusters (num-clusters test-bcl-bytes))
    (last (map byte->basecall (drop bcl-header-size test-bcl-bytes)))
    
  )
