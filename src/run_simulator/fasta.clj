(ns run-simulator.fasta
  (:require [clojure.string :as str]))


(defn- accumulate-fasta-line
  "Accumulates one more line into a sequence of parsed fasta records.
   Intended for reducing over a sequence of parsed fasta lines.
  (accumulate-line [{:defline '>seq-1'}] 'ACTA') => [{:defline 'seq-1' :seq 'ACTA'}]
  (accumulate-line [{:defline '>seq-1' :seq 'ACTA'}] 'GCGC') => [{:defline 'seq-1' :seq 'ACTAGCGC'}]
  (accumulate-line [{:defline '>seq-1' :seq 'ACTAGCGC'}] '>seq-2') => [{:defline 'seq-1' :seq 'ACTAGCGC'} {:defline 'seq-2'}]
  (accumulate-line [{:defline '>seq-1' :seq 'ACTAGCGC'} {:defline 'seq-2'}] 'GAGA') => [{:defline 'seq-1' :seq 'ACTAGCGC'} {:defline 'seq-2' :seq 'GAGA'}]
  ...etc.
  "
  [coll line]
  (let [line-is-defline (str/starts-with? line ">")
        last-elem-has-seq (contains? (last coll) :sequence)]
        (cond
          line-is-defline (let [defline (str/replace line ">" "")
                                id (first (str/split defline #"\s"))
                                description (str/join " " (rest (str/split defline #"\s")))]
                            (conj coll {:id id :description description}))
          last-elem-has-seq (let [last-elem (last coll)
                                  new-last-elem (assoc last-elem :sequence (str (:sequence last-elem) line))]
                              (conj (into [] (butlast coll)) new-last-elem))
          (not last-elem-has-seq) (let [last-elem (last coll)
                                        new-last-elem (assoc last-elem :sequence line)]
                                    (conj (into [] (butlast coll)) new-last-elem)))))

(defn parse-fasta
  ""
  [fasta-path]
  (let [fasta-str (slurp fasta-path)
        fasta-lines (filter #(not (= "" (str/trim %))) (str/split-lines fasta-str))]
    (reduce accumulate-fasta-line [] fasta-lines)))





(comment
  (def test-fasta-path "test/resources/NC_045512.2.head.fasta")
  (def test-fasta-path "test/resources/multiseqs.fasta")
  (def test-fasta (parse-fasta test-fasta-path))
  (random-subseq (:sequence (first test-fasta)) 200)
  (def fasta-lines (str/split-lines (slurp test-fasta-path)))
  (map #(str/starts-with? % ">") fasta-lines) 
  )
