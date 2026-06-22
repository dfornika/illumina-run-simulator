(ns run-simulator.cli
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def version "v0.2.3")

(defn usage
  "Build the CLI usage/help string from the options summary."
  [options-summary]
  (->> [(str/join " " ["run-simulator" version])
        ""
        "Usage: java -jar run-simulator.jar OPTIONS"
        ""
        "Options:"
        options-summary]
       (str/join \newline)))


(def valid-instrument-types #{"nextseq" "miseq" "i100"})

(def options
  [["-c" "--config CONFIG_FILE" "Config file"
    :validate [#(.exists (io/as-file %)) #(str "Config file '" % "' does not exist.")]]
   ["-t" "--instrument-type TYPE" "Instrument type (nextseq, miseq, i100)"
    :default "nextseq"
    :validate [#(contains? valid-instrument-types %) #(str "Invalid instrument type '" % "'. Must be one of: nextseq, miseq, i100")]]
   ["-i" "--instrument-id ID" "Instrument ID (eg. VH00123). If omitted, a random instrument of the specified type is selected."]
   ["-o" "--output-dir DIR" "Output directory (overrides config)"]
   ["-n" "--num-runs N" "Number of runs to generate"
    :default 1
    :parse-fn #(Integer/parseInt %)
    :validate [pos? "Must be a positive integer"]]
   ["-h" "--help"]
   ["-v" "--version"]])

(defn exit
  "Exit the program with status code and message"
  [status msg]
  (println msg)
  (System/exit status))
