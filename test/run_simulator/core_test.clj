(ns run-simulator.core-test
  (:require [clojure.test :as t]
            [clojure.string :as str]
            [run-simulator.core :as core]))


(t/deftest indexes-by-set-unit
  (t/testing "Groups indexes by Index_Set"
    (let [indexes [{:Index_Set "A" :index "AAAA"}
                   {:Index_Set "A" :index "CCCC"}
                   {:Index_Set "B" :index "GGGG"}]
          result (core/indexes-by-set indexes)]
      (t/is (= 2 (count result)))
      (t/is (= 2 (count (get result "A"))))
      (t/is (= 1 (count (get result "B"))))))
  (t/testing "Empty input returns empty map"
    (t/is (= {} (core/indexes-by-set [])))))


(t/deftest random-plate-size-unit
  (t/testing "Result is always between 1 and max"
    (doseq [_ (range 100)]
      (let [size (core/random-plate-size 96)]
        (t/is (>= size 1))
        (t/is (<= size 96)))))
  (t/testing "Max of 1 always returns 1"
    (t/is (= 1 (core/random-plate-size 1)))))


(t/deftest miseq-fastq-subdir-unit
  (t/testing "Old structure returns fixed path"
    (t/is (= "Data/Intensities/BaseCalls"
             (core/miseq-fastq-subdir :old "2024-01-15"))))
  (t/testing "New structure includes Alignment and date"
    (let [result (core/miseq-fastq-subdir :new "2024-01-15")]
      (t/is (str/starts-with? result "Alignment_1"))
      (t/is (str/includes? result "20240115"))
      (t/is (str/ends-with? result "Fastq"))))
  (t/testing "3-arity with demultiplexing number"
    (let [result (core/miseq-fastq-subdir :new 2 "2024-01-15")]
      (t/is (str/starts-with? result "Alignment_2")))))


(t/deftest generate-plate-unit
  (t/testing "Generates correct number of samples"
    (let [indexes (mapv (fn [i] {:Index_Set "A"
                                  :Index_Plate_Well (str "A" (format "%02d" (inc i)))
                                  :index (str "ACGT" i)
                                  :index2 (str "TGCA" i)
                                  :I7_Index_ID (str "N" (format "%03d" i))
                                  :I5_Index_ID (str "S" (format "%03d" i))})
                        (range 10))
          plate (core/generate-plate {:plate-num 1
                                       :instrument-type :miseq
                                       :indexes indexes
                                       :project {:name "TestProject" :species {"SARS-CoV-2" 1.0}}
                                       :num-samples 5})]
      (t/is (= 5 (count (:samples plate))))
      (t/is (= 1 (:plate-num plate)))))
  (t/testing "Limits to 96 samples max"
    (let [indexes (mapv (fn [i] {:Index_Set "A"
                                  :Index_Plate_Well (str "A" (format "%02d" (inc i)))
                                  :index (str "ACGT" i)})
                        (range 200))
          plate (core/generate-plate {:plate-num 1
                                       :instrument-type :miseq
                                       :indexes indexes
                                       :project {:name "TestProject" :species {"SARS-CoV-2" 1.0}}
                                       :num-samples 200})]
      (t/is (<= (count (:samples plate)) 96)))))


(t/deftest generate-plates-unit
  (t/testing "Generates the requested number of plates"
    (let [indexes-a (mapv (fn [i] {:Index_Set "A"
                                    :Index_Plate_Well (str "A" (format "%02d" (inc i)))
                                    :index (str "AAAA" i)})
                          (range 10))
          indexes-b (mapv (fn [i] {:Index_Set "B"
                                    :Index_Plate_Well (str "B" (format "%02d" (inc i)))
                                    :index (str "BBBB" i)})
                          (range 10))
          idx-by-set {"A" indexes-a "B" indexes-b}
          plates (core/generate-plates {:num-plates 2
                                         :starting-plate-num 1
                                         :instrument-type :miseq
                                         :indexes-by-set idx-by-set
                                         :projects [{:name "P1" :species {"SARS-CoV-2" 1.0}}]})]
      (t/is (= 2 (count plates)))
      (t/is (= 1 (:plate-num (first plates))))
      (t/is (= 2 (:plate-num (second plates))))))
  (t/testing "Limits plates to available index sets"
    (let [indexes-a (mapv (fn [i] {:Index_Set "A"
                                    :Index_Plate_Well (str "A" (format "%02d" (inc i)))
                                    :index (str "AAAA" i)})
                          (range 10))
          idx-by-set {"A" indexes-a}
          plates (core/generate-plates {:num-plates 5
                                         :starting-plate-num 1
                                         :instrument-type :miseq
                                         :indexes-by-set idx-by-set
                                         :projects [{:name "P1" :species {"SARS-CoV-2" 1.0}}]})]
      (t/is (= 1 (count plates))))))
