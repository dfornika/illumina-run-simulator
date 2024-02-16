(ns user
  (:require [run-simulator.util :as util]
            [run-simulator.core :as core]))

(defonce db (atom {}))

(defn setup
  ""
  []
  (let [opts {:options {:config "dev-config.edn"}}
        config (util/load-edn! (get-in opts [:options :config]))]
    (do
      (swap! db assoc :config config)
      (swap! db assoc :current-run-num-by-instrument-id
             (into {} (map (juxt :instrument-id :starting-run-number) (get-in @db [:config :instruments]))))
      (swap! db assoc :current-date (util/iso-date-str->date (get-in @db [:config :starting-date])))
      (swap! db assoc :current-plate-number (get-in @db [:config :starting-plate-number])))))

(comment
  
  (reset! db {})

  (setup)

  (core/simulate-run! db)
  )

