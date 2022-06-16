(ns run-simulator.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as spec-gen]
            [clojure.spec.test.alpha :as spec-test]
            [run-simulator.cli :as cli]
            [run-simulator.generators :as generators])
  (:import [java.time.format DateTimeFormatter]
           [java.time ZonedDateTime]
           [java.time LocalDate]
           [java.io File])
  (:gen-class))


(defonce db (atom {}))


(defn log!
  [m]
  (println (json/write-str m :key-fn #(str/replace (name %) "-" "_") :escape-slash false)))

(defn now!
  "Generate an ISO-8601 timestamp (`YYYY-MM-DDTHH:mm:ss.xxxx-OFFSET`).
   eg: `2022-02-11T14:13:49.9335-08:00`
  
   takes:
   returns:
     ISO-8601 timestamp for the current time (`String`)
  "
  []
  (.. (ZonedDateTime/now) (format DateTimeFormatter/ISO_OFFSET_DATE_TIME)))


(defn iso-date-str->date
  [iso-date-str]
  (LocalDate/parse iso-date-str))


(defn load-edn!
  "Load edn from an io/reader source (filename or io/resource).
   https://clojuredocs.org/clojure.edn/read#example-5a68f384e4b09621d9f53a79
  takes:
     `source`: Path to edn file to load (`String`)
   returns:
     Data structure as determined by `source`.
  "
  [source]
  (try
    (with-open [r (io/reader source)]
      (edn/read (java.io.PushbackReader. r)))

    (catch java.io.IOException e
      (printf "Couldn't open '%s': %s\n" source (.getMessage e)))
    (catch RuntimeException e
      (printf "Error parsing edn file '%s': %s\n" source (.getMessage e)))))


(def miseq-run-id-regex #"^[0-9]{6}_M[0-9]{5}_000000000-[A-Z0-9]{5}$")


(spec/def ::miseq-run-id (spec/and string? #(re-matches miseq-run-id-regex %)))


(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            (map keyword)    ;; Drop if you want string keys instead
            repeat)
	  (rest csv-data)))


(defn maps->csv-data 
  "Takes a collection of maps and returns csv-data 
   (vector of vectors with all values)."       
  [maps headers]
  (let [rows (mapv #(mapv % headers) maps)]
    (into [(map name headers)] rows)))


(defn load-csv!
  [source]
  (with-open [reader (io/reader source)]
    (doall
     (csv-data->maps (csv/read-csv reader)))))


(defn serialize-key-value-section
  [data header]
  (let [data-lines (map (juxt (comp name first) second) data)
        data-lines-csv (map #(str/join "," %) data-lines)]
    (str/join "\n" (conj data-lines-csv header))))


(defn maps->csv-str
  [maps headers]
  (->> maps
       (#(maps->csv-data % headers))
       (map #(str/join "," %))
       (str/join "\n")))


(defn serialize-miseq-samplesheet
  ""
  [samplesheet]
  (let [header (serialize-key-value-section (:Header samplesheet) "[Header]")
        reads (str/join "\n" (conj (seq (:Reads samplesheet)) "[Reads]"))
        settings (serialize-key-value-section (:Settings samplesheet) "[Settings]")
        data-section-keys [:Sample_ID :Sample_Name :Sample_Plate :Sample_Well :Index_Plate_Well :I7_Index_ID :index :I5_Index_ID :index2 :Sample_Project :Description]
        data (str/join "\n" ["[Data]" (maps->csv-str (map #(select-keys % data-section-keys) (:Data samplesheet)) data-section-keys)])]
    (str/join "\n" [header
                    reads
                    settings
                    data
                    ""])))


(defn serialize-nextseq-samplesheet
  ""
  [samplesheet]
  (let [header (serialize-key-value-section (:Header samplesheet) "[Header]")
        reads (serialize-key-value-section (:Reads samplesheet) "[Reads]")
        sequencing-settings (serialize-key-value-section (:Sequencing_Settings samplesheet) "[Sequencing_Settings]")
        bclconvert-settings (serialize-key-value-section (:BCLConvert_Settings samplesheet) "[BCLConvert_Settings]")
        bclconvert-data-section-keys [:Sample_ID :Index :Index2]
        bclconvert-data (str/join "\n" ["[BCLConvert_Data]" (maps->csv-str (map #(select-keys % bclconvert-data-section-keys) (:BCLConvert_Data samplesheet)) bclconvert-data-section-keys)])
        cloud-settings (serialize-key-value-section (:Cloud_Settings samplesheet) "[Cloud_Settings]")
        cloud-data-section-keys [:Sample_ID :ProjectName :LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName]
        cloud-data (str/join "\n" ["[Cloud_Data]" (maps->csv-str (map #(select-keys % cloud-data-section-keys) (:Cloud_Data samplesheet)) cloud-data-section-keys)])]
    (str/join "\n" [header
                    reads
                    sequencing-settings
                    bclconvert-settings
                    bclconvert-data
                    cloud-settings
                    cloud-data
                    ""])))


(defn int->well
  "Given an integer `n`, return the well that corresponds. 0 -> A01, 95 -> H12. Wrap back to A01 for integers > 95 and repeat as needed."
  [n]
  (let [n-mod-96 (mod n 96)
        rows ["A" "B" "C" "D" "E" "F" "G" "H"]
        row (rows (quot n-mod-96 12))
        col (format "%02d" (inc (rem n-mod-96 12)))]
    (str row col)))


(spec/fdef int->well
  :args (spec/cat :n int?)
  :ret (spec/and string?
                 #(contains? #{\A \B \C \D \E \F \G \H} (first %))
                 #(= 3 (count %))))


(defn generate-run-id
  [date-str instrument-id run-num instrument-type]
  (let [six-digit-date (apply str (drop 2 (str/replace date-str "-" "")))
        run-id (case instrument-type
                 :miseq (first (spec-gen/sample generators/miseq-flowcell-id 1))
                 :nextseq (first (spec-gen/sample generators/nextseq-flowcell-id 1)))]
    (str/join "_" [six-digit-date
                   instrument-id
                   run-num
                   run-id])))


(defn randomly-select
  [coll]
  (let [num-items (count coll)]
    (nth coll (rand-int num-items))))


(defn generate-sample-id
  [plate-num index]
  (str/join "-" [(first (spec-gen/sample generators/container-id 1))
                 plate-num
                 (:Index_Set index)
                 (:Index_Plate_Well index)]))


(defn generate-sample
  ;; {:miseq-data-headers [:Sample_ID :Sample_Name :Sample_Plate :Sample_Well :Index_Plate_Well :I7_Index_ID :index :I5_Index_ID :index2 :Sample_Project :Description]
  ;;  :nextseq-bclconvert-data-headers  [:Sample_ID :Index :Index2]
  ;;  :nextseq-cloud-data-headers [:Sample_ID :ProjectName :LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName]}
  [plate-num instrument-type index db]
  (let [sample-id (generate-sample-id plate-num index)
        index-with-sample-id (assoc index :Sample_ID sample-id)
        project-id (randomly-select (conj (get-in @db [:config :projects]) ""))
        project-key (case instrument-type :miseq :Sample_Project :nextseq :ProjectName)
        index-with-sample-id-and-project-id (assoc index-with-sample-id project-key project-id)
        blank-fields (case instrument-type
                       :miseq [:Sample_Name :Sample_Plate :Sample_Well :Description]
                       :nextseq [:LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName])]
    (reduce #(assoc % %2 "") index-with-sample-id-and-project-id blank-fields)))


(defn generate-samples
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
  ([output-dir-strucutre-type db]
   (case output-dir-strucutre-type
     :old "Data/Intensities/BaseCalls"
     :new (str (io/file "Alignment_1" (str (str/replace (str (:current-date @db)) "-" "") "_" (first (spec-gen/sample generators/six-digit-timestamp 1))) "Fastq"))))
  ([output-dir-strucutre-type demultiplexing-num db]
   (case output-dir-strucutre-type
     :old "Data/Intensities/BaseCalls"
     :new (str (io/file (str "Alignment_" demultiplexing-num) (str (str/replace (str (:current-date @db)) "-" "") "_" (first (spec-gen/sample generators/six-digit-timestamp 1))) "Fastq")))))


(defn simulate-run!
  [db]
  (let [current-date (str (:current-date @db))
        instrument (randomly-select (get-in @db [:config :instruments]))
        instrument-id (:instrument-id instrument)
        run-num (get-in @db [:current-run-num-by-instrument-id instrument-id])
        instrument-type (:instrument-type instrument)
        run-id (generate-run-id current-date instrument-id run-num instrument-type)
        run-output-dir (io/file (:output-dir instrument) run-id)
        indexes-file (case instrument-type :miseq (io/resource "indexes-miseq.csv") :nextseq (io/resource "indexes-nextseq.csv"))
        indexes (load-csv! indexes-file)
        num-samples (rand-int (count indexes))
        samples (map #(generate-sample (:current-plate-number @db) instrument-type % db) (take num-samples indexes))
        samplesheet-template (case instrument-type :miseq (load-edn! (io/resource "samplesheet-template-miseq.edn")) :nextseq (load-edn! (io/resource "samplesheet-template-nextseq.edn")))
        samplesheet-data (case instrument-type :miseq (assoc samplesheet-template :Data samples) :nextseq (assoc samplesheet-template :BCLConvert_Data samples :Cloud_Data samples))
        samplesheet-string (case instrument-type :miseq (serialize-miseq-samplesheet samplesheet-data) :nextseq (serialize-nextseq-samplesheet samplesheet-data))
        samplesheet-file (io/file run-output-dir "SampleSheet.csv")
        fastq-subdir (io/file run-output-dir (case instrument-type :miseq (miseq-fastq-subdir (:output-dir-structure instrument) db) :nextseq "Analysis/1/Data/fastq"))
        upload-complete-file (io/file run-output-dir "upload_complete.json")]
    (.mkdirs run-output-dir)
    (log! {:timestamp (now!) :event "created_run_output_dir" :run-id run-id :run-output-dir (str run-output-dir)})
    (log! {:timestamp (now!) :event "created_samples" :run-id run-id :num-samples (count samples)})
    (spit samplesheet-file samplesheet-string)
    (log! {:timestamp (now!) :event "created_samplesheet_file" :run-id run-id :samplesheet-file (str samplesheet-file)})
    (.mkdirs fastq-subdir)
    (log! {:timestamp (now!) :event "created_fastq_subdir" :run-id run-id :fastq-subdir (str fastq-subdir)})
    (doseq [[sample-num sample] (map-indexed vector samples)] (.createNewFile (io/file fastq-subdir (str (:Sample_ID sample) "_S" sample-num "_L001_R1_001.fastq.gz"))))
    (doseq [[sample-num sample] (map-indexed vector samples)] (.createNewFile (io/file fastq-subdir (str (:Sample_ID sample) "_S" sample-num "_L001_R2_001.fastq.gz"))))
    (log! {:timestamp (now!) :event "created_fastq_symlinks" :run-id run-id :fastq-subdir (str fastq-subdir) :num-symlinks (* num-samples 2)})
    (.createNewFile upload-complete-file)
    (log! {:timestamp (now!) :event "created_upload_complete_file" :run-id run-id :upload-complete-file (str upload-complete-file)})
    (swap! db update-in [:current-run-num-by-instrument-id instrument-id] inc)))


(defn -main
  ""
  [& args]

  (def opts (parse-opts args cli/options))

  (when (not (empty? (:errors opts)))
    (cli/exit 1 (str/join \newline (:errors opts))))

  ;;
  ;; Handle -h and --help flags
  (if (get-in opts [:options :help])
    (let [options-summary (:summary opts)]
      (cli/exit 0 (cli/usage options-summary))))

  ;;
  ;; Handle -v and --version flags
  (if (get-in opts [:options :version])
    (cli/exit 0 cli/version))

  (let [config (load-edn! (get-in opts [:options :config]))]
    (swap! db assoc :config config))

  (swap! db assoc :current-run-num-by-instrument-id
         (into {} (map (juxt :instrument-id :starting-run-number) (get-in @db [:config :instruments]))))

  (swap! db assoc :current-date (iso-date-str->date (get-in @db [:config :starting-date])))

  (swap! db assoc :current-plate-number (get-in @db [:config :starting-plate-number]))

  
  (loop []

    (simulate-run! db)
    (Thread/sleep (get-in @db [:config :run-interval-ms]))
    (swap! db update :current-date #(.plusDays % 1))
    (recur))

  )

(comment
  (def opts {:options {:config "config.edn"}})
  (spec/conform ::miseq-run-id "220608_M00325_000000000-A6G32")
  (spec/conform ::nextseq-run-id "220608_VH00123_000000000A6G32")
  (spec-gen/generate (spec/gen ::miseq-run-id))
  (spec-gen/sample generators/miseq-flowcell-id 1)
  (def nextseq-bclconvert-data-headers [:Sample_ID :Index :Index2])
  (def nextseq-cloud-data-headers [:Sample_ID :ProjectName :LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName])
  (def miseq-data-headers [:Sample_ID :Sample_Name :Sample_Plate :Sample_Well :Index_Plate_Well :I7_Index_ID :index :I5_Index_ID :index2 :Sample_Project :Description])
  
  (def nextseq-samplesheet-template (load-edn! (io/resource "samplesheet-template-nextseq.edn")))
  (def miseq-samplesheet-template (load-edn! (io/resource "samplesheet-template-miseq.edn")))
  )
