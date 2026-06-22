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


(def options
  [["-c" "--config CONFIG_FILE" "Config file"
    :validate [#(.exists (io/as-file %)) #(str "Config file '" % "' does not exist.")]]
   ["-h" "--help"]
   ["-v" "--version"]])

(defn exit
  "Exit the program with status code and message"
  [status msg]
  (println msg)
  (System/exit status))
