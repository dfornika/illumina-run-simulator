(ns run-simulator.fastq
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [run-simulator.sequence :as seq])
  (:import [java.util.zip GZIPOutputStream]))


(defn format-fastq-header
  "Format an Illumina FASTQ header: @instrument:run:flowcell:lane:tile:x:y read:filter:control:index."
  [{:keys [instrument run-number flowcell-id lane tile x-pos y-pos
           read is-filtered control-number index-sequence]}]
  (str "@" (str/join ":" [instrument run-number flowcell-id lane tile x-pos y-pos])
       " " (str/join ":" [read (or is-filtered "N") (or control-number 0) index-sequence])))


(defn format-fastq-record
  "Assemble a 4-line FASTQ record from header, sequence, and quality strings."
  [header seq-str qual-str]
  (str header "\n" seq-str "\n+\n" qual-str))


(defn generate-read-pair
  "Generate one paired-end read pair (R1 forward, R2 reverse complement) with quality scores and errors."
  [{:keys [reference-seq read-length read-index
           instrument run-number flowcell-id lane tile
           index-sequence error-profile]}]
  (let [fragment-length (min (* 2 read-length) (count reference-seq))
        effective-read-length (min read-length (quot fragment-length 2))
        {:keys [subseq]} (seq/random-subseq reference-seq fragment-length)
        {:keys [r1 r2]} (seq/reads-from-seq subseq effective-read-length)
        r1-quals (seq/generate-quality-scores effective-read-length error-profile)
        r2-quals (seq/generate-quality-scores effective-read-length error-profile)
        r1-seq (seq/introduce-errors r1 r1-quals)
        r2-seq (seq/introduce-errors r2 r2-quals)
        r1-qual-str (seq/quality-scores->string r1-quals)
        r2-qual-str (seq/quality-scores->string r2-quals)
        x-pos (+ 1000 (* read-index 10) (rand-int 5))
        y-pos (+ 1000 (* read-index 10) (rand-int 5))
        base-header {:instrument instrument
                     :run-number run-number
                     :flowcell-id flowcell-id
                     :lane lane
                     :tile tile
                     :x-pos x-pos
                     :y-pos y-pos
                     :is-filtered "N"
                     :control-number 0
                     :index-sequence index-sequence}]
    {:r1-record (format-fastq-record
                 (format-fastq-header (assoc base-header :read 1))
                 r1-seq r1-qual-str)
     :r2-record (format-fastq-record
                 (format-fastq-header (assoc base-header :read 2))
                 r2-seq r2-qual-str)}))


(defn- select-reference-seq
  "Select a reference sequence for one read. With probability (1 - cross-contamination-rate),
   returns the primary; otherwise picks uniformly from contaminants."
  [primary-reference-seq contaminant-reference-seqs cross-contamination-rate]
  (if (or (zero? cross-contamination-rate)
          (empty? contaminant-reference-seqs)
          (>= (rand) cross-contamination-rate))
    primary-reference-seq
    (rand-nth (vec (vals contaminant-reference-seqs)))))


(defn generate-sample-fastq
  "Generate R1 and R2 FASTQ content for a sample. Most reads come from the primary reference;
   a fraction (cross-contamination-rate) are stochastically drawn from contaminant references."
  [{:keys [primary-reference-seq contaminant-reference-seqs cross-contamination-rate
           read-length num-reads
           instrument run-number flowcell-id lane tile
           index-sequence error-profile]}]
  (let [read-pairs (map (fn [i]
                          (let [ref-seq (select-reference-seq primary-reference-seq
                                                              contaminant-reference-seqs
                                                              (or cross-contamination-rate 0))]
                            (generate-read-pair
                             {:reference-seq ref-seq
                              :read-length read-length
                              :read-index i
                              :instrument instrument
                              :run-number run-number
                              :flowcell-id flowcell-id
                              :lane lane
                              :tile tile
                              :index-sequence index-sequence
                              :error-profile error-profile})))
                        (range num-reads))]
    {:r1-content (str (str/join "\n" (map :r1-record read-pairs)) "\n")
     :r2-content (str (str/join "\n" (map :r2-record read-pairs)) "\n")}))


(defn write-fastq-gz!
  "Write string content as a gzip-compressed file."
  [path content]
  (with-open [out (-> (io/output-stream (io/file path))
                      (GZIPOutputStream.)
                      (io/writer))]
    (.write out ^String content)))


(defn write-sample-fastqs!
  "Generate and write gzipped R1 and R2 FASTQ files for a sample using Illumina naming conventions."
  [fastq-subdir sample-num sample fastq-params]
  (let [sample-id (:Sample_ID sample)
        index-seq (str (or (:index sample) (:Index sample) "")
                       "+"
                       (or (:index2 sample) (:Index2 sample) ""))
        {:keys [r1-content r2-content]}
        (generate-sample-fastq (assoc fastq-params :index-sequence index-seq))
        r1-path (io/file fastq-subdir (str sample-id "_S" sample-num "_L001_R1_001.fastq.gz"))
        r2-path (io/file fastq-subdir (str sample-id "_S" sample-num "_L001_R2_001.fastq.gz"))]
    (write-fastq-gz! r1-path r1-content)
    (write-fastq-gz! r2-path r2-content)))
