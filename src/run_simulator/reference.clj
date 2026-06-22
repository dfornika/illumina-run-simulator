(ns run-simulator.reference
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [run-simulator.fasta :as fasta]))


(defn load-registry!
  "Read the reference genome registry from resources/references.edn."
  []
  (with-open [r (io/reader (io/resource "references.edn"))]
    (edn/read (java.io.PushbackReader. r))))


(defn load-references!
  "Load all reference genomes from the registry, returning {species-name sequence-string}."
  []
  (let [registry (load-registry!)]
    (into {}
          (map (fn [{:keys [species fasta]}]
                 (let [path (io/resource (str "references/" fasta))
                       parsed (fasta/parse-fasta path)]
                   [species (:sequence (first parsed))])))
          registry)))
