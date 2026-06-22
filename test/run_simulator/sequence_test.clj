(ns run-simulator.sequence-test
  (:require [clojure.test :as t]
            [run-simulator.sequence :as seq]))


(t/deftest reverse-complement-unit
  (t/testing "Simple cases"
    (t/is (= "ACGT" (seq/reverse-complement "ACGT")))
    (t/is (= "" (seq/reverse-complement "")))
    (t/is (= "A" (seq/reverse-complement "T")))
    (t/is (= "AAAT" (seq/reverse-complement "ATTT"))))
  (t/testing "Handles N"
    (t/is (= "NCA" (seq/reverse-complement "TGN"))))
  (t/testing "Handles lowercase"
    (t/is (= "acgt" (seq/reverse-complement "acgt")))
    (t/is (= "aaat" (seq/reverse-complement "attt"))))
  (t/testing "Involution: applying twice returns original"
    (let [s "ACGTACGT"]
      (t/is (= s (seq/reverse-complement (seq/reverse-complement s)))))))


(t/deftest random-subseq-unit
  (t/testing "Returned subseq has correct length"
    (let [s "ACGTACGTACGT"
          result (seq/random-subseq s 4)]
      (t/is (= 4 (count (:subseq result))))))
  (t/testing "Indices are consistent with subseq"
    (let [s "ACGTACGTACGT"
          result (seq/random-subseq s 4)]
      (t/is (= (:subseq result)
               (subs s (:start-index-inclusive result) (:end-index-exclusive result))))))
  (t/testing "When len > sequence length, returns full sequence"
    (let [s "ACGT"
          result (seq/random-subseq s 100)]
      (t/is (= s (:subseq result)))
      (t/is (= 0 (:start-index-inclusive result)))
      (t/is (= 4 (:end-index-exclusive result))))))


(t/deftest reads-from-seq-unit
  (t/testing "R1 comes from start, R2 is reverse complement from end"
    (let [s "ACGTTTTTGGGG"
          {:keys [r1 r2]} (seq/reads-from-seq s 4)]
      (t/is (= "ACGT" r1))
      (t/is (= "CCCC" r2))))
  (t/testing "Output lengths match requested length"
    (let [{:keys [r1 r2]} (seq/reads-from-seq "AACCGGTTAACCGGTT" 5)]
      (t/is (= 5 (count r1)))
      (t/is (= 5 (count r2))))))


(t/deftest flat-error-profile-unit
  (t/testing "Always returns the constant value"
    (t/is (= 0.01 (seq/flat-error-profile 0 0.01)))
    (t/is (= 0.01 (seq/flat-error-profile 150 0.01)))
    (t/is (= 0.5 (seq/flat-error-profile 999 0.5)))))


(t/deftest logistic-error-profile-unit
  (t/testing "Error rate increases with position"
    (let [early (seq/logistic-error-profile 0 0.1 0.05 200)
          late (seq/logistic-error-profile 300 0.1 0.05 200)]
      (t/is (< early late))))
  (t/testing "At inflection point x0, value is approximately L/2 + offset"
    (let [L 0.1
          v (seq/logistic-error-profile 200 L 0.05 200)]
      (t/is (< (Math/abs (- v (+ 0.0001 (/ L 2)))) 0.001)))))


(t/deftest probability->phred-unit
  (t/testing "Known conversions"
    (t/is (= 10 (seq/probability->phred 0.1)))
    (t/is (= 20 (seq/probability->phred 0.01)))
    (t/is (= 30 (seq/probability->phred 0.001)))))


(t/deftest phred->char-unit
  (t/testing "PHRED 0 is !"
    (t/is (= \! (seq/phred->char 0))))
  (t/testing "PHRED 30 is ?"
    (t/is (= \? (seq/phred->char 30))))
  (t/testing "PHRED 40 is I"
    (t/is (= \I (seq/phred->char 40)))))


(t/deftest generate-quality-scores-unit
  (t/testing "Returns correct number of scores"
    (t/is (= 150 (count (seq/generate-quality-scores 150 {})))))
  (t/testing "All scores are within clamped range [2, 41]"
    (let [scores (seq/generate-quality-scores 200 {:L 0.1 :k 0.05 :x0 200})]
      (t/is (every? #(and (>= % 2) (<= % 41)) scores)))))


(t/deftest quality-scores->string-unit
  (t/testing "Correct encoding"
    (t/is (= "!#?" (seq/quality-scores->string [0 2 30]))))
  (t/testing "Output length matches input"
    (let [scores [10 20 30 40]]
      (t/is (= (count scores) (count (seq/quality-scores->string scores)))))))


(t/deftest introduce-errors-unit
  (t/testing "High quality preserves sequence"
    (let [s "ACGTACGT"
          high-quals (repeat 8 40)]
      (t/is (= 8 (count (seq/introduce-errors s high-quals))))))
  (t/testing "Output length matches input"
    (let [s "ACGTACGT"
          quals [2 2 2 2 40 40 40 40]]
      (t/is (= (count s) (count (seq/introduce-errors s quals))))))
  (t/testing "All output bases are valid DNA"
    (let [s "ACGTACGT"
          quals [5 5 5 5 5 5 5 5]
          result (seq/introduce-errors s quals)]
      (t/is (every? #{\A \C \G \T} result)))))
