(ns run-simulator.samplesheet
  (:require [clojure.string :as str]
            [run-simulator.util :as util]))


(defn serialize-key-value-section
  "For a key-value section of a samplesheet, serialize to string."
  [data header]
  (let [data-lines (map (juxt (comp name first) second) data)
        data-lines-csv (map #(str/join "," %) data-lines)]
    (str/join "\n" (conj data-lines-csv header))))


(defn serialize-miseq-samplesheet
  "Take samplesheet data and convert to a string in
   SampleSheet.csv format for MiSeq"
  [samplesheet]
  (let [header (serialize-key-value-section (:Header samplesheet) "[Header]")
        reads (str/join "\n" (conj (seq (:Reads samplesheet)) "[Reads]"))
        settings (serialize-key-value-section (:Settings samplesheet) "[Settings]")
        data-section-keys [:Sample_ID :Sample_Name :Sample_Plate :Sample_Well :Index_Plate_Well :I7_Index_ID :index :I5_Index_ID :index2 :Sample_Project :Description]
        data (str/join "\n" ["[Data]" (util/maps->csv-str (map #(select-keys % data-section-keys) (:Data samplesheet)) data-section-keys)])]
    (str/join "\n" [header
                    reads
                    settings
                    data
                    ""])))


(defn serialize-nextseq-samplesheet
  "Take samplesheet data and convert to a string in
   SampleSheet.csv format for a NextSeq"
  [samplesheet]
  (let [header (serialize-key-value-section (:Header samplesheet) "[Header]")
        reads (serialize-key-value-section (:Reads samplesheet) "[Reads]")
        sequencing-settings (serialize-key-value-section (:Sequencing_Settings samplesheet) "[Sequencing_Settings]")
        bclconvert-settings (serialize-key-value-section (:BCLConvert_Settings samplesheet) "[BCLConvert_Settings]")
        bclconvert-data-section-keys [:Sample_ID :Index :Index2]
        bclconvert-data (str/join "\n" ["[BCLConvert_Data]" (util/maps->csv-str (map #(select-keys % bclconvert-data-section-keys) (:BCLConvert_Data samplesheet)) bclconvert-data-section-keys)])
        cloud-settings (serialize-key-value-section (:Cloud_Settings samplesheet) "[Cloud_Settings]")
        cloud-data-section-keys [:Sample_ID :ProjectName :LibraryName :LibraryPrepKitUrn :LibraryPrepKitName :IndexAdapterKitUrn :IndexAdapterKitName]
        cloud-data (str/join "\n" ["[Cloud_Data]" (util/maps->csv-str (map #(select-keys % cloud-data-section-keys) (:Cloud_Data samplesheet)) cloud-data-section-keys)])]
    (str/join "\n" [header
                    reads
                    sequencing-settings
                    bclconvert-settings
                    bclconvert-data
                    cloud-settings
                    cloud-data
                    ""])))
