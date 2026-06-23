(ns run-simulator.runparameters
  (:require [clojure.java.io :as io]
            [clojure.data.xml :as xml]
            [run-simulator.util :as util]))



(defn make-xml-node
  "Convert a [key value] pair to an XML element, recursing into nested maps."
  [[k v]]
  (if (map? v)
    (xml/element k {} (map make-xml-node (seq v)))
    (xml/element k {} v)))


(defn serialize-map-to-xml
  "Convert a nested map to an indented XML string via make-xml-node."
  [m]
  (->> m
       (map make-xml-node)
       xml/indent-str))


(defn generate-runparameters-data-miseq
  "Generate RunParameters data for a MiSeq run by merging run-specific values into the template."
  [flowcell-id]
  (let [template (util/load-edn! (io/resource "runparameters-template-miseq.edn"))]
    (-> template
        (assoc-in [:RunParameters :FlowcellRFIDTag :SerialNumber] flowcell-id))))


(defn generate-runparameters-data-nextseq
  "Generate RunParameters data for a NextSeq run by merging run-specific values into the template."
  [instrument-id flowcell-id read-length output-dir]
  (let [template (util/load-edn! (io/resource "runparameters-template-nextseq.edn"))]
    (-> template
        (assoc-in [:RunParameters :InstrumentSerialNumber] instrument-id)
        (assoc-in [:RunParameters :FlowCellSerialNumber] flowcell-id)
        (assoc-in [:RunParameters :OutputFolder] (str output-dir))
        (assoc-in [:RunParameters :PlannedCycles :Read1] read-length)
        (assoc-in [:RunParameters :PlannedCycles :Read2] read-length)
        (assoc-in [:RunParameters :CompletedCycles :Read1] read-length)
        (assoc-in [:RunParameters :CompletedCycles :Read2] read-length))))
