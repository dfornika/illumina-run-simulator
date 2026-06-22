(ns run-simulator.reference
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [run-simulator.fasta :as fasta]))


(defn load-registry!
  []
  (with-open [r (io/reader (io/resource "references.edn"))]
    (edn/read (java.io.PushbackReader. r))))


(defn load-references!
  []
  (let [registry (load-registry!)]
    (into {}
          (map (fn [{:keys [species fasta]}]
                 (let [path (io/resource (str "references/" fasta))
                       parsed (fasta/parse-fasta path)]
                   [species (:sequence (first parsed))])))
          registry)))
