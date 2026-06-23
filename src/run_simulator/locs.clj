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



(defn float->qseq
  "Convert a float coordinate to QSEQ integer format: round(n * 10 + 1000)."
  [n]
  (Math/round (+ (* n 10) 1000)))


(defn num-clusters
  "Read the cluster count from bytes 8-11 of a .locs file header (little-endian)."
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
     :x-qseq (float->qseq x-float)
     :y-float y-float
     :y-qseq (float->qseq y-float)}))


(defn coord->byteseq
  "Convert a coordinate {:x-float :y-float} to an 8-byte little-endian sequence."
  [coord]
  (let [{:keys [x-float y-float]} coord
        x-bytes (util/float->bytes x-float :little-endian)
        y-bytes (util/float->bytes y-float :little-endian)]
    (concat (into [] x-bytes) (into [] y-bytes))))


(defn random-coord
  "Generate a random coordinate within the given x/y bounds, returning both float and QSEQ formats."
  [{:keys [min-x min-y max-x max-y]}]
  (let [x-float (util/rand-between min-x max-x)
        x-qseq (float->qseq x-float)
        y-float (util/rand-between min-y max-y)
        y-qseq (float->qseq y-float)]
    {:x-float x-float
     :x-qseq  x-qseq
     :y-float y-float
     :y-qseq  y-qseq}))
 

(defn write-locs-file!
  "Write a .locs file for a tile. clusters is a seq of maps with :x-float and :y-float."
  [path clusters]
  (let [n (count clusters)
        magic-bytes (util/int->bytes locs-magic-num :little-endian)
        version-bytes (util/float->bytes (float locs-version-num) :little-endian)
        count-bytes (util/int->bytes n :little-endian)]
    (io/make-parents path)
    (with-open [out (io/output-stream (io/file path))]
      (.write out ^bytes magic-bytes)
      (.write out ^bytes version-bytes)
      (.write out ^bytes count-bytes)
      (doseq [cluster clusters]
        (.write out (byte-array (coord->byteseq cluster)))))))
