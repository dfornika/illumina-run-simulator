(ns run-simulator.tile
  (:require [run-simulator.sequence :as seq]
            [run-simulator.locs :as locs]
            [run-simulator.fastq :as fastq]))


(defn generate-cluster-coords
  "Generate float and QSEQ integer coordinates for a cluster."
  []
  (let [{:keys [x-float y-float x-qseq y-qseq]}
        (locs/random-coord {:min-x 0 :min-y 0 :max-x 2500 :max-y 2500})]
    {:x-float x-float
     :y-float y-float
     :x-pos x-qseq
     :y-pos y-qseq}))


(defn generate-cluster
  "Generate a single cluster record with sequences, qualities, coordinates, and metadata."
  [{:keys [cluster-index sample-index sample-id index-sequence
           reference-seq read-length error-profile]}]
  (let [coords (generate-cluster-coords)
        use-random (nil? reference-seq)
        fragment-length (if use-random
                          (* 2 read-length)
                          (min (* 2 read-length) (count reference-seq)))
        effective-read-length (if use-random
                                read-length
                                (min read-length (quot fragment-length 2)))
        {:keys [r1 r2]} (if use-random
                           {:r1 (seq/random-seq effective-read-length)
                            :r2 (seq/random-seq effective-read-length)}
                           (let [{:keys [subseq]} (seq/random-subseq reference-seq fragment-length)]
                             (seq/reads-from-seq subseq effective-read-length)))
        r1-quals (seq/generate-quality-scores effective-read-length error-profile)
        r2-quals (seq/generate-quality-scores effective-read-length error-profile)
        r1-bases (seq/introduce-errors r1 r1-quals)
        r2-bases (seq/introduce-errors r2 r2-quals)]
    (merge coords
           {:cluster-index cluster-index
            :sample-index sample-index
            :sample-id sample-id
            :index-sequence index-sequence
            :r1-bases r1-bases
            :r1-quals r1-quals
            :r2-bases r2-bases
            :r2-quals r2-quals
            :passed-filter true})))


(defn generate-tile-clusters
  "Generate all clusters for a tile across all samples."
  [{:keys [samples fastq-params reference-seqs]}]
  (let [{:keys [read-length error-profile num-reads]} fastq-params]
    (into []
          (mapcat
           (fn [[sample-index sample]]
             (let [sample-id (:Sample_ID sample)
                   index-seq (str (or (:index sample) (:Index sample) "")
                                  "+"
                                  (or (:index2 sample) (:Index2 sample) ""))
                   primary-species (:_primary-species sample)
                   primary-ref (get reference-seqs primary-species)
                   contaminant-refs (dissoc (select-keys reference-seqs (keys (:_species-weights sample))) primary-species)
                   cross-contamination-rate (:_cross-contamination-rate sample 0)]
               (mapv (fn [read-index]
                       (let [ref-seq (fastq/select-reference-seq
                                      primary-ref contaminant-refs
                                      (or cross-contamination-rate 0))]
                         (generate-cluster
                          {:cluster-index (+ (* sample-index num-reads) read-index)
                           :sample-index sample-index
                           :sample-id sample-id
                           :index-sequence index-seq
                           :reference-seq ref-seq
                           :read-length read-length
                           :error-profile error-profile})))
                     (range num-reads)))))
          (map-indexed vector samples))))
