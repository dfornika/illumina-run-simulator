(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'io.github.dfornika/illumina-run-simulator)
(def main-ns 'run-simulator.core)
(def version "0.1.0-alpha")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :main main-ns
           :uber-file uber-file
           :basis basis}))
