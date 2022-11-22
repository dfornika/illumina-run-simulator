(ns run-simulator.util
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.data.csv :as csv])
  (:import  [java.time.format DateTimeFormatter]
            [java.time ZonedDateTime]
            [java.time LocalDate]))


(defn csv-data->maps
  "Takes a seq of seqs (all of equal length). Uses elements of first seq as keys, 
   and associates elements of remaining seqs with those keys in order.
   eg: `(csv-data->maps [[:a :b] [1 2] [3 4]]) => ({:a 1, :b 2} {:a 3, :b 4})`
  "
  [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            (map keyword)    ;; Drop if you want string keys instead
            repeat)
	  (rest csv-data)))


(defn maps->csv-data 
  "Takes a collection of maps and returns csv-data 
   (vector of vectors with all values)."       
  [maps headers]
  (let [rows (mapv #(mapv % headers) maps)]
    (into [(map name headers)] rows)))


(defn load-csv!
  "Read csv from source and return sequence of maps, with keys taken from csv header."
  [source]
  (with-open [reader (io/reader source)]
    (doall
     (csv-data->maps (csv/read-csv reader)))))


(defn maps->csv-str
  ""
  [maps headers]
  (->> maps
       (#(maps->csv-data % headers))
       (map #(str/join "," %))
       (str/join "\n")))


(defn log!
  ""
  [m]
  (println (json/write-str m :key-fn #(str/replace (name %) "-" "_") :escape-slash false)))


(defn now!
  "Generate an ISO-8601 timestamp (`YYYY-MM-DDTHH:mm:ss.xxxx-OFFSET`).
   eg: `2022-02-11T14:13:49.9335-08:00`
  
   takes:
   returns:
     ISO-8601 timestamp for the current time (`String`)
  "
  []
  (.. (ZonedDateTime/now) (format DateTimeFormatter/ISO_OFFSET_DATE_TIME)))


(defn iso-date-str->date
  "
  "
  [iso-date-str]
  (LocalDate/parse iso-date-str))


(defn load-edn!
  "Load edn from an io/reader source (filename or io/resource).
   https://clojuredocs.org/clojure.edn/read#example-5a68f384e4b09621d9f53a79
  takes:
     `source`: Path to edn file to load (`String`)
   returns:
     Data structure as determined by `source`.
  "
  [source]
  (try
    (with-open [r (io/reader source)]
      (edn/read (java.io.PushbackReader. r)))

    (catch java.io.IOException e
      (printf "Couldn't open '%s': %s\n" source (.getMessage e)))
    (catch RuntimeException e
      (printf "Error parsing edn file '%s': %s\n" source (.getMessage e)))))


(defn randomly-select
  ""
  [coll]
  (let [num-items (count coll)]
    (if (> num-items 0)
      (nth coll (rand-int num-items))
      nil)))


(defn int->well
  "Given an integer `n`, return the well that corresponds. 0 -> A01, 95 -> H12. Wrap back to A01 for integers > 95 and repeat as needed."
  [n]
  (let [n-mod-96 (mod n 96)
        rows ["A" "B" "C" "D" "E" "F" "G" "H"]
        row (rows (quot n-mod-96 12))
        col (format "%02d" (inc (rem n-mod-96 12)))]
    (str row col)))


#_(spec/fdef int->well
  :args (spec/cat :n int?)
  :ret (spec/and string?
                 #(contains? #{\A \B \C \D \E \F \G \H} (first %))
                 #(= 3 (count %))))
