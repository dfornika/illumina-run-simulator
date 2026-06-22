(ns run-simulator.filter
  (:require [clojure.java.io :as io]
            [run-simulator.util :as util]))


(def filter-version 3)


(defn write-filter-file!
  "Write a .filter file for a tile. clusters is a seq of maps with :passed-filter."
  [path clusters]
  (let [n (count clusters)
        zero-bytes (util/int->bytes 0 :little-endian)
        version-bytes (util/int->bytes filter-version :little-endian)
        count-bytes (util/int->bytes n :little-endian)
        data (byte-array (map (fn [cluster]
                                (if (:passed-filter cluster)
                                  (unchecked-byte 1)
                                  (unchecked-byte 0)))
                              clusters))]
    (io/make-parents path)
    (with-open [out (io/output-stream (io/file path))]
      (.write out ^bytes zero-bytes)
      (.write out ^bytes version-bytes)
      (.write out ^bytes count-bytes)
      (.write out ^bytes data))))
