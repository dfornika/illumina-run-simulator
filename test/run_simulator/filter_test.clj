(ns run-simulator.filter-test
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
            [run-simulator.filter :as filt]
            [run-simulator.util :as util]))


(t/deftest write-filter-file-unit
  (t/testing "Written filter file has correct header and data"
    (let [clusters [{:passed-filter true}
                    {:passed-filter false}
                    {:passed-filter true}]
          tmp-file (io/file "test_output" "filter_write_test" "test.filter")]
      (try
        (filt/write-filter-file! tmp-file clusters)
        (let [bytes (vec (util/slurp-bytes (str tmp-file)))
              header-zeros (util/bytes->int (byte-array (take 4 bytes)) :little-endian)
              version (util/bytes->int (byte-array (subvec bytes 4 8)) :little-endian)
              n (util/bytes->int (byte-array (subvec bytes 8 12)) :little-endian)
              data (subvec bytes 12)]
          (t/is (= 0 header-zeros))
          (t/is (= 3 version))
          (t/is (= 3 n))
          (t/is (= [1 0 1] (mapv #(Byte/toUnsignedInt %) data))))
        (finally
          (io/delete-file tmp-file true)
          (doseq [d [(io/file "test_output" "filter_write_test")
                     (io/file "test_output")]]
            (.delete d)))))))
