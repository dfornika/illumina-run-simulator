(ns run-simulator.runparameters
  (:require [clojure.java.io :as io]
            [clojure.data.xml :as xml]
            [clojure.walk :refer [postwalk]]
            [run-simulator.util :as util]))



(defn make-xml-node
  "
   credit to: https://stackoverflow.com/a/56019931
  "
  [[k v]]
  (if (map? v)
    (xml/element k {} (map make-xml-node (seq v)))
    (xml/element k {} v)))


(defn serialize-map-to-xml
  ""
  [m]
  (->> m
       (map make-xml-node)
       xml/indent-str))



(comment
  (def runparameters-template-miseq (util/load-edn! (io/resource "runparameters-template-miseq.edn")))
  (def runparameters-template-nextseq (util/load-edn! (io/resource "runparameters-template-nextseq.edn")))

  (def runparameters-data-miseq
    (let [template runparameters-template-miseq]
      (-> template
          (assoc-in [:RunParameters :FlowcellRFIDTag :SerialNumber] "000000000-GA63B"))))

  (def runparameters-data-nextseq
    (let [template runparameters-template-nextseq]
      (-> template
          identity)))

  (serialize-map-to-xml runparameters-data-miseq)
  (serialize-map-to-xml runparameters-data-nextseq)
  (serialize-map-to-xml {:a  {:b 2 :c {:d 3 :e 4}}})
  )
