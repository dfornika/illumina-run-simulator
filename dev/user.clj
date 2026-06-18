(ns user
  (:require [clj-reload.core :as reload]
            [run-simulator.core :as core]
            [run-simulator.util :as util]))

(reload/init
  {:dirs ["src" "dev" "test"]})

(comment
  (reload/reload)
  ,)

(defonce db (atom {}))

(comment

  (let [config (util/load-edn! "dev-config.edn")]
    (swap! db assoc :config config)
    (swap! db assoc :current-run-num-by-instrument-id
           (into {} (map (juxt :instrument-id :starting-run-number) (get-in @db [:config :instruments]))))
    (swap! db assoc :current-date (util/iso-date-str->date (get-in @db [:config :starting-date])))
    (swap! db assoc :current-plate-number (get-in @db [:config :starting-plate-number])))

  (core/simulate-run! db)
  (core/simulate-run! db {:instrument-type :i100})
  (core/simulate-run! db {:instrument-type :miseq})
  (core/simulate-run! db {:instrument-type :nextseq})
  ,)
