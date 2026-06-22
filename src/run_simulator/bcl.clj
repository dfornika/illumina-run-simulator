(ns run-simulator.bcl
  (:require [run-simulator.util :as util])
  (:import java.nio.ByteBuffer))

(def bcl-header-size 4)

(defn byte->base
  "Extract the base call (A/C/G/T) from the lower 2 bits of a BCL byte."
  [b]
  (let [base-num (bit-and 2r00000011 b)
        base-map {0 \A 1 \C 2 \G 3 \T}]
    (base-map base-num)))

(defn qual->char
  "Convert a quality score integer to its ASCII character (PHRED+33)."
  [q]
  (char (+ 33 q)))


(defn byte->qual
  "Extract the quality score from the upper 6 bits of a BCL byte."
  [b]
  (bit-shift-right (bit-and 0xff b) 2))


(defn byte->basecall
  "Decode a BCL byte into {:base :qual-int}. A null byte (0x00) returns N with quality 2."
  [b]
  (if (= 0x00 b)
    {:base \N :qual-int 2}
    {:base (byte->base b)
     :qual-int (byte->qual b)}))


(defn num-clusters
  "Read the cluster count from the 4-byte BCL file header (little-endian)."
  [bcl-bytes]
  (let [num-clusters-bytes (take 4 bcl-bytes)]
    (util/bytes->long (byte-array num-clusters-bytes) :little-endian)))

(comment
    (def test-bcl-path "test/resources/picard_bcl_passing.bcl")
    (def test-bcl-bytes (util/slurp-bytes test-bcl-path))
    (def num-test-bcl-clusters (num-clusters test-bcl-bytes))
    (last (map byte->basecall (drop bcl-header-size test-bcl-bytes)))
    
  )
