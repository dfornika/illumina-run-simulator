(ns run-simulator.runcompletionstatus
  (:require [clojure.java.io :as io]
            [run-simulator.runparameters :as runparameters]
            [run-simulator.util :as util]))


(defn generate-runcompletionstatus-data-miseq
  "Generate RunCompletionStatus data for a MiSeq run."
  [run-id read-length]
  (let [template (util/load-edn! (io/resource "runcompletionstatus-template-miseq.edn"))
        total-cycles (str (+ (* 2 read-length) 20))]
    (-> template
        (assoc-in [:RunCompletionStatus :RunId] run-id)
        (assoc-in [:RunCompletionStatus :TotalCycles] total-cycles)
        (assoc-in [:RunCompletionStatus :CycleCompleted] total-cycles))))


(defn generate-runcompletionstatus-data-nextseq
  "Generate RunCompletionStatus data for a NextSeq run."
  []
  (util/load-edn! (io/resource "runcompletionstatus-template-nextseq.edn")))
