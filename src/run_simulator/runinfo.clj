(ns run-simulator.runinfo
  (:require [clojure.string :as str]
            [clojure.data.xml :as xml]
            [run-simulator.util :as util]))


(defn generate-tiles
  []
  (let [tiles (for [y (range 11 25)
                    x (map #(format "%02d" %) (range 1 5))]
                [:Tile (str "1_" y x)])]
  (reduce conj [:Tiles] tiles)))


(defn generate-runinfo-data-miseq
  ""
  [run-id read-len]
  (let [run-id-split (str/split run-id #"_")
        date (first run-id-split)
        instrument-id (nth run-id-split 1)
        run-number (nth run-id-split 2)
        flowcell-id (last run-id-split)]
    [:RunInfo {:Version 2}
     [:Run {:Id run-id :Number run-number}
      [:Flowcell flowcell-id]
      [:Instrument instrument-id]
      [:Date date]
      [:Reads
       [:Read {:NumCycles read-len :Number 1 :IsIndexedRead "N"}]
       [:Read {:NumCycles       10 :Number 2 :IsIndexedRead "Y"}]
       [:Read {:NumCycles       10 :Number 3 :IsIndexedRead "Y"}]
       [:Read {:NumCycles read-len :Number 4 :IsIndexedRead "N"}]]
      [:FlowcellLayout {:LaneCount 1 :SurfaceCount 2 :SwathCount 1 :TileCount 4}]]]))


(defn generate-runinfo-data-nextseq
  ""
  [run-id read-len]
  (let [run-id-split (str/split run-id #"_")
        date (str (str/split (util/now-utc!) #"\.") "Z")
        instrument-id (nth run-id-split 1)
        run-number 1
        flowcell-id (last run-id-split)]
    [:RunInfo {:Version 2}
     [:Run {:Id run-id :Number run-number}
      [:Flowcell flowcell-id]
      [:Instrument instrument-id]
      [:Date date]
      [:Reads
       [:Read {:NumCycles read-len :Number 1 :IsIndexedRead "N"}]
       [:Read {:NumCycles       10 :Number 2 :IsIndexedRead "Y"}]
       [:Read {:NumCycles       10 :Number 3 :IsIndexedRead "Y"}]
       [:Read {:NumCycles read-len :Number 4 :IsIndexedRead "N"}]]
      [:FlowcellLayout {:LaneCount 1 :SurfaceCount 2 :SwathCount 4 :TileCount 4}
       [:TileSet {:TileNamingConvention "FourDigit"}
        (generate-tiles)
        ]]
      [:ImageDimensions {:Width 8208 :Height 5541}]
      [:ImageChannels
       [:Name "green"]
       [:Name "blue"]]]]))


(comment

  (def runinfo-data-nextseq
    [:RunInfo {:Version 6}
     [:Run {:Id "220602_VH00278_48_AM78GB61" :Number 1}
      [:Flowcell "AM78GB61"]
      [:Instrument "VH00278"]
      [:Date (str (first (str/split (util/now-utc!) #"\.")) "Z")]
      [:Reads
       [:Read {:Number 1 :NumCycles 151 :IsIndexedRead "N" :IsReverseComplement "N"}]
       [:Read {:Number 2 :NumCycles  10 :IsIndexedRead "Y" :IsReverseComplement "N"}]
       [:Read {:Number 3 :NumCycles  10 :IsIndexedRead "Y" :IsReverseComplement "Y"}]
       [:Read {:Number 4 :NumCycles 151 :IsIndexedRead "N" :IsReverseComplement "N"}]]
      [:FlowcellLayout {:LaneCount 1 :SurfaceCount 2 :SwathCount 4 :TileCount 4}
       [:TileSet {:TileNamingConvention "FourDigit"}
        [:Tiles
         [:Tile "1_1101"]
         [:Tile "1_1102"]
         [:Tile "1_1103"]
         [:Tile "1_1104"]
         [:Tile "1_1201"]
         [:Tile "1_1202"]
         [:Tile "1_1203"]
         [:Tile "1_1204"]]]]
      [:ImageDimensions {:Width 8208 :Height 5541}]
      [:ImageChannels
       [:Name "green"]
       [:Name "blue"]]]])

  (def runinfo-data-miseq
    [:RunInfo {:Version 2}
     [:Run {:Id "220602_M00123_46_000000000-G7B3A" :Number 46}
      [:Flowcell "000000000-G7B3A"]
      [:Instrument "M00123"]
      [:Date "220602"]
      [:Reads
       [:Read {:NumCycles 151 :Number 1 :IsIndexedRead "N"}]
       [:Read {:NumCycles  10 :Number 2 :IsIndexedRead "Y"}]
       [:Read {:NumCycles  10 :Number 3 :IsIndexedRead "Y"}]
       [:Read {:NumCycles 151 :Number 4 :IsIndexedRead "N"}]]
      [:FlowcellLayout {:LaneCount 1 :SurfaceCount 2 :SwathCount 1 :TileCount 4}]]])
  
  (print (xml/indent-str (xml/sexp-as-element runinfo-data-nextseq)))
  (print (xml/indent-str (xml/sexp-as-element runinfo-data-miseq)))

  )



