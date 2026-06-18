(ns run-simulator.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as spec-gen]
            [run-simulator.cli :as cli]
            [run-simulator.util :as util]
            [run-simulator.generators :as generators]
            [run-simulator.samplesheet :as samplesheet])
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
        run-id (case instrument-type
                 :miseq (first (spec-gen/sample generators/miseq-flowcell-id 1))
                 :nextseq (first (spec-gen/sample generators/nextseq-flowcell-id 1))
                 :i100 (first (spec-gen/sample generators/i100-flowcell-id 1)))]
    (str/join "_" [date
                   instrument-id
                   run-num
                   run-id])))


(defn generate-sample-id
  ""
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
        project-key (case instrument-type
                      :miseq :Sample_Project
                      :nextseq :ProjectName
                      :i100 :ProjectName)
        index-with-sample-id-and-project-id (assoc index-with-sample-id project-key project-id)
        blank-fields (case instrument-type
                       :miseq [:Sample_Name :Sample_Plate :Sample_Well :Description]
                       :nextseq [:LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName]
                       :i100 [:LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName])]
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
  [db & {:keys [instrument-type]}]
  (let [current-date (str (:current-date @db))
        available-instruments (get-in @db [:config :instruments])
        available-instruments (if instrument-type
                                (filter #(= instrument-type (:instrument-type %)) available-instruments)
                                available-instruments)
        _ (util/log! {:available-instruments available-instruments})
        instrument (util/randomly-select available-instruments)
        instrument-id (:instrument-id instrument)
        instrument-type (:instrument-type instrument)
        run-num (get-in @db [:current-run-num-by-instrument-id instrument-id])
        padded-run-num (case instrument-type
                         :miseq (format "%04d" run-num)
                         :nextseq (format "%d" run-num)
                         :i100 (format "%04d" run-num))
        run-id (generate-run-id current-date instrument-id padded-run-num instrument-type)
        run-output-dir (io/file (:output-dir instrument) run-id)
        indexes-file (case instrument-type
                       :miseq (io/resource "indexes-miseq.csv")
                       :nextseq (io/resource "indexes-nextseq.csv")
                       :i100 (io/resource "indexes-i100.csv"))
        indexes (util/load-csv! indexes-file)
        num-samples (rand-int (count indexes))
        samples (map #(generate-sample (:current-plate-number @db) instrument-type % db) (take num-samples indexes))
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
                                               :miseq (miseq-fastq-subdir (:output-dir-structure instrument) db)
                                               :nextseq "Analysis/1/Data/fastq"
                                               :i100 "Analysis/1/Data/BCLConvert/fastq"))
        upload-complete-file (io/file run-output-dir "upload_complete.json")
        qc-check-complete-file (io/file run-output-dir "qc_check_complete.json")]
    
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
      (.createNewFile (io/file fastq-subdir (str (:Sample_ID sample) "_S" sample-num "_L001_R1_001.fastq.gz"))))
    (doseq [[sample-num sample] (map-indexed vector samples)]
      (.createNewFile (io/file fastq-subdir (str (:Sample_ID sample) "_S" sample-num "_L001_R2_001.fastq.gz"))))
    
    (util/log! {:timestamp (util/now!)
                :event "created_fastq_symlinks"
                :run-id run-id
                :fastq-subdir (str fastq-subdir)
                :num-symlinks (* num-samples 2)})
    
    (when (get-in @db [:config :mark-upload-complete])
      (.createNewFile upload-complete-file)
      (util/log! {:timestamp (util/now!)
                  :event "created_upload_complete_file"
                  :run-id run-id
                  :upload-complete-file (str upload-complete-file)}))

    (when (get-in @db [:config :mark-qc-check-complete])
      (.createNewFile qc-check-complete-file)
      (util/log! {:timestamp (util/now!)
                  :event "created_qc_check_complete_file"
                  :run-id run-id
                  :qc-check-complete-file (str qc-check-complete-file)}))
    
    (swap! db update-in [:current-run-num-by-instrument-id instrument-id] inc)
    
    {:instrument-type instrument-type
     :run-id run-id
     :num-samples num-samples
     :output-dir run-output-dir
     :samplesheet-files samplesheet-files}))


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
    (swap! db update :current-date #(.plusDays % (util/rand-int-range 1 10)))
    (recur)))


(comment
  (def opts {:options {:config "config.edn"}})
  )
