(ns run-simulator.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as spec-gen]
            [run-simulator.cli :as cli]
            [run-simulator.util :as util]
            [run-simulator.generators :as generators]
            [run-simulator.samplesheet :as samplesheet]
            [run-simulator.runparameters :as runparameters])
  (:gen-class))


(defonce db (atom {}))


(defn generate-run-id
  "Generate a sequencing run ID"
  [date-str instrument-id run-num instrument-type]
  (let [six-digit-date (apply str (drop 2 (str/replace date-str "-" "")))
        run-id (case instrument-type
                 :miseq   (first (spec-gen/sample generators/miseq-flowcell-id 1))
                 :nextseq (first (spec-gen/sample generators/nextseq-flowcell-id 1)))]
    (str/join "_" [six-digit-date
                   instrument-id
                   run-num
                   run-id])))


(defn generate-sample-id
  "Generate a sample ID"
  [plate-num index]
  (str/join "-" [(first (spec-gen/sample generators/container-id 1))
                 plate-num
                 (:Index_Set index)
                 (:Index_Plate_Well index)]))


(defn generate-sample
  ;; {:miseq-data-headers [:Sample_ID :Sample_Name :Sample_Plate :Sample_Well :Index_Plate_Well :I7_Index_ID :index :I5_Index_ID :index2 :Sample_Project :Description]
  ;;  :nextseq-bclconvert-data-headers  [:Sample_ID :Index :Index2]
  ;;  :nextseq-cloud-data-headers [:Sample_ID :ProjectName :LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName]}
  ""
  [plate-num instrument-type index db]
  (let [sample-id (generate-sample-id plate-num index)
        index-with-sample-id (assoc index :Sample_ID sample-id)
        project-id (util/randomly-select (conj (get-in @db [:config :projects]) ""))
        project-key (case instrument-type :miseq :Sample_Project :nextseq :ProjectName)
        index-with-sample-id-and-project-id (assoc index-with-sample-id project-key project-id)
        blank-fields (case instrument-type
                       :miseq [:Sample_Name :Sample_Plate :Sample_Well :Description]
                       :nextseq [:LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName])]
    (reduce #(assoc % %2 "") index-with-sample-id-and-project-id blank-fields)))


(defn generate-samples
  ""
  [num-samples indexes instrument-type db]
  (loop [num-generated 1
         plate-num (get @db :current-plate-number)
         available-indexes indexes
         samples []]
    (if (>= num-generated num-samples)
      samples
      (recur (inc num-generated)
             (do (when (= (mod num-generated 96) 0)
                   (swap! db update :current-plate-number inc))
                 (get @db :current-plate-number))
             (drop 1 available-indexes)
             (conj samples (generate-sample plate-num instrument-type (first available-indexes) db))))))


(defn miseq-fastq-subdir
  ""
  ([output-dir-strucutre-type db]
   (case output-dir-strucutre-type
     :old "Data/Intensities/BaseCalls"
     :new (str (io/file "Alignment_1" (str (str/replace (str (:current-date @db)) "-" "") "_" (first (spec-gen/sample generators/six-digit-timestamp 1))) "Fastq"))))
  ([output-dir-strucutre-type demultiplexing-num db]
   (case output-dir-strucutre-type
     :old "Data/Intensities/BaseCalls"
     :new (str (io/file (str "Alignment_" demultiplexing-num) (str (str/replace (str (:current-date @db)) "-" "") "_" (first (spec-gen/sample generators/six-digit-timestamp 1))) "Fastq")))))


(defn simulate-run!
  ""
  [db]
  (let [current-date (str (:current-date @db))
        instrument (util/randomly-select (get-in @db [:config :instruments]))
        instrument-id (:instrument-id instrument)
        run-num (get-in @db [:current-run-num-by-instrument-id instrument-id])
        instrument-type (:instrument-type instrument)
        run-id (generate-run-id current-date instrument-id run-num instrument-type)
        run-output-dir (io/file (:output-dir instrument) run-id)
        indexes-file (case instrument-type
                       :miseq (io/resource "indexes-miseq.csv")
                       :nextseq (io/resource "indexes-nextseq.csv"))
        indexes (util/load-csv! indexes-file)
        num-samples (rand-int (count indexes))
        samples (map #(generate-sample (:current-plate-number @db) instrument-type % db) (take num-samples indexes))
        samplesheet-template (case instrument-type
                               :miseq (util/load-edn! (io/resource "samplesheet-template-miseq.edn"))
                               :nextseq (util/load-edn! (io/resource "samplesheet-template-nextseq.edn")))
        samplesheet-data (case instrument-type
                           :miseq (assoc samplesheet-template :Data samples)
                           :nextseq (assoc samplesheet-template :BCLConvert_Data samples :Cloud_Data samples))
        samplesheet-string (case instrument-type
                             :miseq (samplesheet/serialize-miseq-samplesheet samplesheet-data)
                             :nextseq (samplesheet/serialize-nextseq-samplesheet samplesheet-data))
        samplesheet-file (io/file run-output-dir "SampleSheet.csv")
        runparameters-template (case instrument-type
                               :miseq (util/load-edn! (io/resource "runparameters-template-miseq.edn"))
                               :nextseq (util/load-edn! (io/resource "runparameters-template-nextseq.edn")))
        runparameters-data (case instrument-type
                             :miseq runparameters-template    ;; TODO
                             :nextseq runparameters-template) ;; Replace template values with simulated values
        runparameters-file (io/file run-output-dir "RunParameters.xml")
        runparameters-string (runparameters/serialize-map-to-xml runparameters-data)
        fastq-subdir (io/file run-output-dir (case instrument-type
                                               :miseq (miseq-fastq-subdir (:output-dir-structure instrument) db)
                                               :nextseq "Analysis/1/Data/fastq"))
        upload-complete-file (io/file run-output-dir "upload_complete.json")]
    (.mkdirs run-output-dir)
    (util/log! {:timestamp (util/now!) :event "created_run_output_dir" :run-id run-id :run-output-dir (str run-output-dir)})
    (spit runparameters-file runparameters-string)
    (util/log! {:timestamp (util/now!) :event "created_runparameters_file" :run-id run-id :runparameters-file (str runparameters-file)})
    (util/log! {:timestamp (util/now!) :event "created_samples" :run-id run-id :num-samples (count samples)})
    (spit samplesheet-file samplesheet-string)
    (util/log! {:timestamp (util/now!) :event "created_samplesheet_file" :run-id run-id :samplesheet-file (str samplesheet-file)})
    (.mkdirs fastq-subdir)
    (util/log! {:timestamp (util/now!) :event "created_fastq_subdir" :run-id run-id :fastq-subdir (str fastq-subdir)})
    (doseq [[sample-num sample] (map-indexed vector samples)] (.createNewFile (io/file fastq-subdir (str (:Sample_ID sample) "_S" sample-num "_L001_R1_001.fastq.gz"))))
    (doseq [[sample-num sample] (map-indexed vector samples)] (.createNewFile (io/file fastq-subdir (str (:Sample_ID sample) "_S" sample-num "_L001_R2_001.fastq.gz"))))
    (util/log! {:timestamp (util/now!) :event "created_fastq_symlinks" :run-id run-id :fastq-subdir (str fastq-subdir) :num-symlinks (* num-samples 2)})
    (when (get-in @db [:config :mark-upload-complete])
      (.createNewFile upload-complete-file)
      (util/log! {:timestamp (util/now!) :event "created_upload_complete_file" :run-id run-id :upload-complete-file (str upload-complete-file)}))
    (swap! db update-in [:current-run-num-by-instrument-id instrument-id] inc)))


(defn -main
  "Main entry point."
  [& args]

  (def opts (parse-opts args cli/options))

  (when (not (empty? (:errors opts)))
    (cli/exit 1 (str/join \newline (:errors opts))))

  ;;
  ;; Handle -h and --help flags
  (when (get-in opts [:options :help])
    (let [options-summary (:summary opts)]
      (cli/exit 0 (cli/usage options-summary))))

  ;;
  ;; Handle -v and --version flags
  (when (get-in opts [:options :version])
    (cli/exit 0 cli/version))

  (let [config (util/load-edn! (get-in opts [:options :config]))]
    (swap! db assoc :config config))

  (swap! db assoc :current-run-num-by-instrument-id
         (into {} (map (juxt :instrument-id :starting-run-number) (get-in @db [:config :instruments]))))

  (swap! db assoc :current-date (util/iso-date-str->date (get-in @db [:config :starting-date])))

  (swap! db assoc :current-plate-number (get-in @db [:config :starting-plate-number]))

  
  (loop []
    (simulate-run! db)
    (Thread/sleep (get-in @db [:config :run-interval-ms]))
    (swap! db update :current-date #(.plusDays % 1))
    (recur)))


(comment
  (def opts {:options {:config "config.edn"}})
  )
