(ns run-simulator.generators-test
  (:require [clojure.test :as t]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [run-simulator.generators :as generators]))


(t/deftest miseq-run-id-matches-regex
  (t/testing "Generated MiSeq run IDs match expected pattern"
    (doseq [id (gen/sample generators/miseq-run-id 20)]
      (t/is (re-matches generators/miseq-run-id-regex id) (str "Failed for: " id)))))


(t/deftest nextseq-run-id-matches-regex
  (t/testing "Generated NextSeq run IDs match expected pattern"
    (doseq [id (gen/sample generators/nextseq-run-id 20)]
      (t/is (re-matches generators/nextseq-run-id-regex id) (str "Failed for: " id)))))


(t/deftest i100-run-id-matches-regex
  (t/testing "Generated i100 run IDs match expected pattern"
    (doseq [id (gen/sample generators/i100-run-id 20)]
      (t/is (re-matches generators/i100-run-id-regex id) (str "Failed for: " id)))))


(t/deftest specs-have-generators
  (t/testing "Specs can produce values via s/gen"
    (t/is (s/valid? ::generators/miseq-run-id (gen/generate (s/gen ::generators/miseq-run-id))))
    (t/is (s/valid? ::generators/nextseq-run-id (gen/generate (s/gen ::generators/nextseq-run-id))))
    (t/is (s/valid? ::generators/i100-run-id (gen/generate (s/gen ::generators/i100-run-id))))))
