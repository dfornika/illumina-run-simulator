(ns run-simulator.bcl
  (:require [clojure.java.io :as io]
            [run-simulator.util :as util]))

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
    (util/bytes->int (byte-array num-clusters-bytes) :little-endian)))

(def ^:private base->bits
  {\A 0 \C 1 \G 2 \T 3})


(defn encode-bcl-byte
  "Encode a base character and quality score into a single BCL byte."
  [base qual]
  (if (= \N base)
    (unchecked-byte 0)
    (unchecked-byte (bit-or (bit-shift-left (min qual 63) 2)
                            (base->bits base)))))


(defn write-bcl-file!
  "Write one BCL file for a single cycle. clusters is a seq of cluster maps,
   read-key is :r1-bases or :r2-bases, qual-key is :r1-quals or :r2-quals,
   cycle-index is the 0-based position within that read."
  [path clusters read-key qual-key cycle-index]
  (let [n (count clusters)
        header (util/int->bytes n :little-endian)
        data (byte-array (map (fn [cluster]
                                (let [base (nth (read-key cluster) cycle-index)
                                      qual (nth (qual-key cluster) cycle-index)]
                                  (encode-bcl-byte base qual)))
                              clusters))]
    (io/make-parents path)
    (with-open [out (io/output-stream (io/file path))]
      (.write out ^bytes header)
      (.write out ^bytes data))))


(defn write-tile-bcl-files!
  "Write all BCL files for a tile: one file per cycle for R1 and R2."
  [base-dir lane tile clusters read-length]
  (let [lane-str (format "L%03d" lane)
        tile-str (str tile)]
    (dotimes [i read-length]
      (let [cycle-num (inc i)
            cycle-dir (format "C%d.1" cycle-num)
            path (io/file base-dir lane-str cycle-dir (str "s_" lane "_" tile-str ".bcl"))]
        (write-bcl-file! path clusters :r1-bases :r1-quals i)))
    (dotimes [i read-length]
      (let [cycle-num (+ read-length (inc i))
            cycle-dir (format "C%d.1" cycle-num)
            path (io/file base-dir lane-str cycle-dir (str "s_" lane "_" tile-str ".bcl"))]
        (write-bcl-file! path clusters :r2-bases :r2-quals i)))))
