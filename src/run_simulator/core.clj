(ns run-simulator.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [run-simulator.cli :as cli]
            [run-simulator.generators :as generators]
            )
  (:import [java.time.format DateTimeFormatter]
           [java.time ZonedDateTime]
           [java.time LocalDate]
           [java.io File])
  (:gen-class))


(defonce db (atom {}))

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

(s/def ::miseq-run-id (s/and string? #(re-matches miseq-run-id-regex %)))

(defn maps->csv-data 
  "Takes a collection of maps and returns csv-data 
   (vector of vectors with all values)."       
   [maps]
   (let [columns (-> maps first keys)
         headers (mapv name columns)
         rows (mapv #(mapv % columns) maps)]
     (into [headers] rows)))


(defn serialize-key-value-section
  [data header]
  (let [data-lines (map (juxt (comp name first) second) data)
        data-lines-csv (map #(str/join "," %) data-lines)]
    (str/join "\n" (conj data-lines-csv header))))



(defn serialize-miseq-samplesheet
  ""
  [samplesheet]
  (let [header (serialize-key-value-section (:Header samplesheet) "[Header]")
        reads (str/join "\n" (conj (seq (:Reads samplesheet)) "[Reads]"))
        settings (serialize-key-value-section (:Settings samplesheet) "[Settings]")
        data "[Data]"]
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
        bclconvert-data "[BCLConvert_Data]"
        cloud-settings (serialize-key-value-section (:Cloud_Settings samplesheet) "[Cloud_Settings]")
        cloud-data "[Cloud_Data]"]
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
        col (format "%02d" (inc (rem n-mod-96 12)))
        ]
    (str row col)))


(defn generate-run-id
  [date-str instrument-id run-num instrument-type]
  (let [six-digit-date (apply str (drop 2 (str/replace date-str "-" "")))
        run-id (case instrument-type
                 :miseq (first (gen/sample generators/miseq-flowcell-id 1))
                 :nextseq (first (gen/sample generators/nextseq-flowcell-id 1)))]
    (str/join "_" [six-digit-date
                   instrument-id
                   run-num
                   run-id])))



(defn simulate-run!
  [db]
  (let [current-date (str (:current-date @db))
        instrument-id "M00123"
        run-num 100
        instrument-type :miseq
        run-id (generate-run-id current-date instrument-id run-num instrument-type) ]
    (println (str (now!) " Simulating run... " run-id))))


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
         (into {} (map (juxt :instrument-id (fn [x] (identity 0))) (get-in @db [:config :instruments]))))

  (swap! db assoc :current-date (iso-date-str->date (get-in @db [:config :starting-date])))

  
  (loop []

    (simulate-run! db)
    (Thread/sleep (get-in @db [:config :run-interval-ms]))
    (swap! db update :current-date #(.plusDays % 1))
    (recur))

  )

(comment
  (def opts {:options {:config "config.edn"}})
  (s/conform ::miseq-run-id "220608_M00325_000000000-A6G32")
  (s/conform ::nextseq-run-id "220608_VH00123_000000000A6G32")
  (gen/generate (s/gen ::miseq-run-id))
  (gen/sample generators/miseq-flowcell-id 1)
  (def nextseq-bclconvert-data-headers [:Sample_ID :Index :Index2])
  (def nextseq-cloud-data-headers [:Sample_ID :ProjectName :LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName])
  (def miseq-data-headers [:Sample_ID :Sample_Name :Sample_Plate :Sample_Well :Index_Plate_Well :I7_Index_ID :index :I5_Index_ID :index2 :Sample_Project :Description])
  
  (def nextseq-samplesheet-template (load-edn! (io/resource "samplesheet-template-nextseq.edn")))
  (def miseq-samplesheet-template (load-edn! (io/resource "samplesheet-template-miseq.edn")))
  )
