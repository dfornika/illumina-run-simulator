(ns run-simulator.runparameters
  (:require [clojure.java.io :as io]
            [clojure.data.xml :as xml]
            [run-simulator.util :as util]))

(def versions-miseq
  {:FPGAVersion "9.5.12"
   :MCSVersion "2.6.2.1"
   :RTAVersion "1.18.54"})


(defn serialize-runparameters-miseq
  [runparameters])


(defn serialize-runparameters-nextseq
  [runparameters]
  )


(comment
  (def runparameters-template-miseq (util/load-edn! (io/resource "runparameters-template-miseq.edn")))
  (def runparameters-template-nextseq (util/load-edn! (io/resource "runparameters-template-nextseq.edn")))

  (def runparameters-data-miseq
    (let [template runparameters-template-miseq]
      template))

  (def runparameters-data-nextseq
    (let [template runparameters-template-nextseq]
      template))
  
  (print (xml/indent-str (xml/sexp-as-element runparameters-data-nextseq)))


  )
