(ns run-simulator.runparameters
  (:require [clojure.data.xml :as xml]))

(def versions-miseq
  {:FPGAVersion "9.5.12"
   :MCSVersion "2.6.2.1"
   :RTAVersion "1.18.54"})



(comment
  (def runparameters-data-miseq
    [:RunParameters
     [:EnableCloud "false"]
     [:CopyManifests "true"]
     [:FlowcellRFIDTag
      [:SerialNumber "000000000-G8B4M"]
      [:PartNumber 15035218]
      [:ExpirationDate "2023-08-08T00:00:00"]]
     [:PR2BottleRFIDTag
      [:SerialNumber "MS3440523-00PR2"]
      [:PartNumber 15041807]
      [:ExpirationDate "2023-08-30T00:00:00"]]
    [:ReagentKitRFIDTag
     [:SerialNumber "MS3366888-300V2"]
     [:PartNumber 15033572]
     [:ExpirationDate "2023-05-15T00:00:00"]]
     ])

  (print (xml/indent-str (xml/sexp-as-element runparameters-data-miseq)))


  )
