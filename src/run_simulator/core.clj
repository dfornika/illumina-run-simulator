(ns run-simulator.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as gen]
            [run-simulator.cli :as cli]
            [run-simulator.util :as util]
            [run-simulator.generators :as generators]
            [run-simulator.samplesheet :as samplesheet]
            [run-simulator.reference :as reference]
            [run-simulator.fastq :as fastq])
  (:gen-class))


(defonce db (atom {}))


(defn generate-run-id
  [date-str instrument-id run-num instrument-type]
  (let [eight-digit-date (str/replace date-str "-" "")
        six-digit-date (apply str (drop 2 eight-digit-date))
        date (case instrument-type
               :miseq six-digit-date
               :nextseq six-digit-date
               :i100 eight-digit-date)
        flowcell-id (case instrument-type
                      :miseq (gen/generate generators/miseq-flowcell-id)
                      :nextseq (gen/generate generators/nextseq-flowcell-id)
                      :i100 (gen/generate generators/i100-flowcell-id))]
    (str/join "_" [date instrument-id run-num flowcell-id])))


(defn generate-sample-id
  [plate-num index]
  (str/join "-" [(gen/generate generators/container-id)
                 plate-num
                 (:Index_Set index)
                 (:Index_Plate_Well index)]))


(defn generate-sample
  [plate-num instrument-type index project]
  (let [sample-id (generate-sample-id plate-num index)
        index-with-sample-id (assoc index :Sample_ID sample-id)
        project-name (:name project)
        project-key (case instrument-type
                      :miseq :Sample_Project
                      :nextseq :ProjectName
                      :i100 :ProjectName)
        index-with-project (assoc index-with-sample-id
                                  project-key project-name
                                  :_species (:species project))
        blank-fields (case instrument-type
                       :miseq [:Sample_Name :Sample_Plate :Sample_Well :Description]
                       :nextseq [:LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName]
                       :i100 [:LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName])]
    (reduce #(assoc % %2 "") index-with-project blank-fields)))


(defn random-plate-size
  [max-samples]
  (if (< (rand) 0.7)
    max-samples
    (max 1 (+ (quot max-samples 2) (rand-int (- max-samples (quot max-samples 2)))))))


(defn load-indexes
  [instrument-type]
  (let [indexes-file (case instrument-type
                       :miseq (io/resource "indexes-miseq.csv")
                       :nextseq (io/resource "indexes-nextseq.csv")
                       :i100 (io/resource "indexes-i100.csv"))]
    (util/load-csv! indexes-file)))


(defn indexes-by-set
  [indexes]
  (into {} (map (fn [[k v]] [k (vec v)]) (group-by :Index_Set indexes))))


(defn generate-plate
  [{:keys [plate-num instrument-type indexes project num-samples]}]
  (let [max-samples (min 96 (count indexes))
        num-samples (min max-samples (or num-samples (random-plate-size max-samples)))
        selected-indexes (take num-samples indexes)
        samples (mapv #(generate-sample plate-num instrument-type % project)
                      selected-indexes)]
    {:plate-num plate-num
     :project project
     :samples samples}))


(defn generate-plates
  [{:keys [num-plates starting-plate-num instrument-type indexes-by-set projects]}]
  (let [available-sets (keys indexes-by-set)
        num-plates (min num-plates (count available-sets))]
    (mapv (fn [i index-set-id]
            (generate-plate
             {:plate-num (+ starting-plate-num i)
              :instrument-type instrument-type
              :indexes (get indexes-by-set index-set-id)
              :project (util/randomly-select projects)}))
          (range num-plates)
          (take num-plates available-sets))))


(defn miseq-fastq-subdir
  ([output-dir-structure-type date-str]
   (case output-dir-structure-type
     :old "Data/Intensities/BaseCalls"
     :new (str (io/file "Alignment_1"
                        (str (str/replace date-str "-" "") "_" (gen/generate generators/six-digit-timestamp))
                        "Fastq"))))
  ([output-dir-structure-type demultiplexing-num date-str]
   (case output-dir-structure-type
     :old "Data/Intensities/BaseCalls"
     :new (str (io/file (str "Alignment_" demultiplexing-num)
                        (str (str/replace date-str "-" "") "_" (gen/generate generators/six-digit-timestamp))
                        "Fastq")))))


(defn generate-run-plan
  [{:keys [current-date instrument run-num plates config]}]
  (let [date-str (str current-date)
        instrument-id (:instrument-id instrument)
        instrument-type (:instrument-type instrument)
        padded-run-num (case instrument-type
                         :miseq (format "%04d" run-num)
                         :nextseq (format "%d" run-num)
                         :i100 (format "%04d" run-num))
        run-id (generate-run-id date-str instrument-id padded-run-num instrument-type)
        run-output-dir (io/file (:output-dir instrument) run-id)
        samples (into [] (mapcat :samples) plates)
        samplesheet-template (case instrument-type
                               :miseq (util/load-edn! (io/resource "samplesheet-template-miseq.edn"))
                               :nextseq (util/load-edn! (io/resource "samplesheet-template-nextseq.edn"))
                               :i100 (util/load-edn! (io/resource "samplesheet-template-i100.edn")))
        samplesheet-data (case instrument-type
                           :miseq (assoc samplesheet-template :Data samples)
                           :nextseq (assoc samplesheet-template :BCLConvert_Data samples :Cloud_Data samples)
                           :i100 (assoc samplesheet-template :BCLConvert_Data samples :Cloud_Data samples))
        samplesheet-string (case instrument-type
                             :miseq (samplesheet/serialize-miseq-samplesheet samplesheet-data)
                             :nextseq (samplesheet/serialize-nextseq-samplesheet samplesheet-data)
                             :i100 (samplesheet/serialize-i100-samplesheet samplesheet-data))
        samplesheet-files (case instrument-type
                            :miseq [(io/file run-output-dir "SampleSheet.csv")]
                            :nextseq [(io/file run-output-dir "SampleSheet.csv")
                                      (io/file run-output-dir "Analysis/1/Data" "SampleSheet.csv")]
                            :i100    [(io/file run-output-dir "SampleSheet.csv")
                                      (io/file run-output-dir "Analysis/1/inputs" "SampleSheet.csv")])
        fastq-subdir (io/file run-output-dir (case instrument-type
                                               :miseq (miseq-fastq-subdir (:output-dir-structure instrument) date-str)
                                               :nextseq "Analysis/1/Data/fastq"
                                               :i100 "Analysis/1/Data/BCLConvert/fastq"))
        read-length (case instrument-type
                      :miseq (first (:Reads samplesheet-template))
                      :nextseq (:Read1Cycles (:Reads samplesheet-template))
                      :i100 (:Read1Cycles (:Reads samplesheet-template)))
        flowcell-id (last (str/split run-id #"_"))
        reads-per-sample (or (:reads-per-sample config) 100)]
    {:run-id run-id
     :instrument-id instrument-id
     :instrument-type instrument-type
     :run-output-dir run-output-dir
     :plates plates
     :samples samples
     :num-samples (count samples)
     :samplesheet-string samplesheet-string
     :samplesheet-files samplesheet-files
     :fastq-subdir fastq-subdir
     :fastq-params {:read-length read-length
                    :instrument instrument-id
                    :run-number padded-run-num
                    :flowcell-id flowcell-id
                    :lane 1
                    :tile 1101
                    :error-profile {:L 0.1 :k 0.05 :x0 200}
                    :num-reads reads-per-sample}
     :mark-upload-complete (:mark-upload-complete config)
     :mark-qc-check-complete (:mark-qc-check-complete config)}))


(defn write-run!
  [{:keys [run-id run-output-dir samples num-samples
           samplesheet-string samplesheet-files
           fastq-subdir fastq-params reference-seqs
           mark-upload-complete mark-qc-check-complete]}]
  (.mkdirs run-output-dir)
  (util/log! {:timestamp (util/now!)
              :event "created_run_output_dir"
              :run-id run-id
              :run-output-dir (str run-output-dir)})

  (util/log! {:timestamp (util/now!)
              :event "created_samples"
              :run-id run-id
              :num-samples (count samples)})

  (doseq [samplesheet-file samplesheet-files]
    (let [samplesheet-dir (io/file (.getParent samplesheet-file))]
      (.mkdirs samplesheet-dir)
      (spit samplesheet-file samplesheet-string)
      (util/log! {:timestamp (util/now!)
                  :event "created_samplesheet_file"
                  :run-id run-id
                  :samplesheet-file (str samplesheet-file)})))

  (.mkdirs fastq-subdir)
  (util/log! {:timestamp (util/now!)
              :event "created_fastq_subdir"
              :run-id run-id
              :fastq-subdir (str fastq-subdir)})

  (doseq [[sample-num sample] (map-indexed vector samples)]
    (let [species-list (:_species sample)
          sample-refs (select-keys reference-seqs species-list)]
      (fastq/write-sample-fastqs! fastq-subdir sample-num sample
                                  (assoc fastq-params :reference-seqs sample-refs))))

  (util/log! {:timestamp (util/now!)
              :event "created_fastq_files"
              :run-id run-id
              :fastq-subdir (str fastq-subdir)
              :num-fastq-files (* num-samples 2)})

  (when mark-upload-complete
    (let [f (io/file run-output-dir "upload_complete.json")]
      (.createNewFile f)
      (util/log! {:timestamp (util/now!)
                  :event "created_upload_complete_file"
                  :run-id run-id
                  :upload-complete-file (str f)})))

  (when mark-qc-check-complete
    (let [f (io/file run-output-dir "qc_check_complete.json")]
      (.createNewFile f)
      (util/log! {:timestamp (util/now!)
                  :event "created_qc_check_complete_file"
                  :run-id run-id
                  :qc-check-complete-file (str f)}))))


(defn simulate-run!
  [db & {:keys [instrument-type]}]
  (let [available-instruments (get-in @db [:config :instruments])
        available-instruments (if instrument-type
                                (filter #(= instrument-type (:instrument-type %)) available-instruments)
                                available-instruments)
        instrument (util/randomly-select available-instruments)
        inst-type (:instrument-type instrument)
        indexes (load-indexes inst-type)
        idx-by-set (indexes-by-set indexes)
        num-plates (inc (rand-int (count idx-by-set)))
        plates (generate-plates
                {:num-plates num-plates
                 :starting-plate-num (:current-plate-number @db)
                 :instrument-type inst-type
                 :indexes-by-set idx-by-set
                 :projects (get-in @db [:config :projects])})
        plan (generate-run-plan
              {:current-date (:current-date @db)
               :instrument instrument
               :run-num (get-in @db [:current-run-num-by-instrument-id (:instrument-id instrument)])
               :plates plates
               :config (:config @db)})]
    (write-run! (assoc plan :reference-seqs (:reference-seqs @db)))
    (swap! db update-in [:current-run-num-by-instrument-id (:instrument-id instrument)] inc)
    (swap! db update :current-plate-number + (count plates))
    plan))


(defn -main
  "Main entry point."
  [& args]

  (def opts (parse-opts args cli/options))

  (when (not (empty? (:errors opts)))
    (cli/exit 1 (str/join \newline (:errors opts))))

  (when (get-in opts [:options :help])
    (let [options-summary (:summary opts)]
      (cli/exit 0 (cli/usage options-summary))))

  (when (get-in opts [:options :version])
    (cli/exit 0 cli/version))

  (let [config (util/load-edn! (get-in opts [:options :config]))]
    (swap! db assoc :config config))

  (swap! db assoc :reference-seqs (reference/load-references!))
  (util/log! {:timestamp (util/now!)
              :event "loaded_reference_genomes"
              :num-references (count (:reference-seqs @db))})

  (swap! db assoc :current-run-num-by-instrument-id
         (into {} (map (juxt :instrument-id :starting-run-number) (get-in @db [:config :instruments]))))

  (swap! db assoc :current-date (util/iso-date-str->date (get-in @db [:config :starting-date])))

  (swap! db assoc :current-plate-number (get-in @db [:config :starting-plate-number]))

  (loop []
    (simulate-run! db)
    (Thread/sleep (get-in @db [:config :run-interval-ms]))
    (swap! db update :current-date #(.plusDays % (util/rand-int-range 1 10)))
    (recur)))


(comment
  (def opts {:options {:config "config.edn"}})
  )
