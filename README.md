# Illumina Run Simulator
This is a helper utility designed to assist with testing and development of [BCCDC-PHL/auto-fastq-symlink](https://github.com/BCCDC-PHL/auto-fastq-symlink).

## Building & Usage
The [clojure command-line](https://clojure.org/guides/install_clojure) package (`clj`) is required for building this tool.

The `build.sh` script in this repo will build an executable uberjar and save it to the `target` directory.

Run the `.jar` file as follows. A config file is required. The format of the config file is described in the next section.

```
java -jar illumina-run-simulator.jar --config config.edn
```

## Configuration
This tool expects an [edn](https://github.com/edn-format/edn)-formatted config file, with the following format:

```edn
{:instruments [{:instrument-id "M00123"
                :output-dir "test_output/M00123/22"
                :instrument-type :miseq
                :starting-run-number 300}
               {:instrument-id "VH00123"
                :output-dir "test_output/VH00123/22"
                :instrument-type :nextseq
                :starting-run-number 12}]
 :projects ["mysterious_experiment"
            "routine_testing"
            "quality_check"
            "viral_outbreak"
            "assay_development"]
 :starting-plate-number 100
 :starting-date "2022-06-01"
 :run-interval-ms 10000}
```
