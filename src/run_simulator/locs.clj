(ns run-simulator.locs
  (:require [clojure.java.io :as io]
            [run-simulator.util :as util])
  (:import java.nio.ByteBuffer))

(def locs-header-size 12)
(def locs-magic-num 1)
(def locs-version-num 1.0)

(def clocs-header-size 5)
(def clocs-image-width 2048)
(def clocs-block-size 25)
(def clocs-num-bins-in-row (int (Math/ceil (/ clocs-image-width clocs-block-size))))


(defn num-clusters
  ""
  [locs-bytes]
  (let [locs-header (take locs-header-size locs-bytes)
        num-clusters-bytes (byte-array (drop 8 locs-header))]
    (util/bytes->int num-clusters-bytes :little-endian)))


(defn byteseq->coord 
  "Takes a sequence of 8 bytes (java.lang.Byte) and converts
  them to both integer and floating-point x/y coordinates:
  {:x-float x-float
   :x-qseq  x-int
   :y-float y-float
   :y-qseq  y-int}
  Convert float to int using the formula found here:
  https://github.com/broadinstitute/picard/blob/828cc4fd2619e05a216304b5019af67dab439569/src/main/java/picard/illumina/parser/readers/AbstractIlluminaPositionFileReader.java#L102-L107 
  "
  [bs]
  (let [[x y] (partition 4 bs)
        x-bytes (byte-array x)
        y-bytes (byte-array y)
        x-float (util/bytes->float x-bytes :little-endian)
        y-float (util/bytes->float y-bytes :little-endian)]
    {:x-float x-float
     :x-qseq (Math/round (+ (* x-float 10) 1000))
     :y-float y-float
     :y-qseq (Math/round (+ (* y-float 10) 1000))}))


(defn coord->byteseq
  ""
  [coord]
  (let [{:keys [x-float y-float]} coord
        x-bytes (util/float->bytes x-float :little-endian)
        y-bytes (util/float->bytes y-float :little-endian)]
    (concat (into [] x-bytes) (into [] y-bytes))))
    
    
  

(comment

  (def test-locs-path "test/resources/picard_s_1_6.locs")
  (def test-locs-bytes (util/slurp-bytes test-locs-path))
  (num-clusters test-locs-bytes)
  (def first-coord (byteseq->coord (take 8 (drop 12 test-locs-bytes))))
  (coord->byteseq first-coord)
  (count (partition 8 (drop 12 test-locs-bytes)))

  (with-open [out-file (io/output-stream (io/file "test_out.locs") :append true)]
    (.write out-file (byte-array (coord->byteseq first-coord)) ))
  (with-open [out-file (io/output-stream (io/file "test_out.locs") :append true)]
    (.write out-file (byte-array (coord->byteseq first-coord)) ))
  
  
  )
