(ns run-simulator.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [run-simulator.cli :as cli])
  (:import [java.time.format DateTimeFormatter]
           [java.time ZonedDateTime]
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

  (println opts)
  (println @db)
  

  )

(comment
  (s/conform ::miseq-run-id "220608_M00325_000000000-A6G32")
  (s/conform ::nextseq-run-id "220608_VH00123_000000000A6G32")
  (gen/generate (s/gen ::miseq-run-id))
  (def nextseq-bclconvert-data-headers [:Sample_ID :Index :Index2])
  (def nextseq-cloud-data-headers [:Sample_ID :ProjectName :LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName])
  (def nextseq-samplesheet-template (load-edn! (io/resource "samplesheet-template-nextseq.edn")))
  )
