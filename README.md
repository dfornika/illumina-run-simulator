# Illumina Run Simulator
Generates simulated Illumina sequencer output directories, complete with realistic FASTQ data derived from bundled reference genomes. Useful for testing and development of tools that process Illumina sequencing runs, such as [BCCDC-PHL/auto-fastq-symlink](https://github.com/BCCDC-PHL/auto-fastq-symlink) and [BCCDC-PHL/illumina-uploader](https://github.com/BCCDC-PHL/illumina-uploader).

Supported instrument types: MiSeq, NextSeq, iSeq 100.

## Building

The [clojure command-line tools](https://clojure.org/guides/install_clojure) are required for building.

Build an executable uberjar:

```
clojure -T:build uber
```

The jar is written to `target/illumina-run-simulator-0.2.3-standalone.jar`.

## Usage

```
java -jar illumina-run-simulator.jar [OPTIONS]
```

### Options

| Flag | Default | Description |
|------|---------|-------------|
| `-c`, `--config CONFIG_FILE` | | Config file (required) |
| `-t`, `--instrument-type TYPE` | `nextseq` | Instrument type: `nextseq`, `miseq`, or `i100` |
| `-i`, `--instrument-id ID` | | Specific instrument ID from config (e.g. `VH00123`). If omitted, a random instrument of the specified type is selected. |
| `-o`, `--output-dir DIR` | | Output directory (overrides config) |
| `-n`, `--num-runs N` | `1` | Number of runs to generate |
| `-h`, `--help` | | Print help |
| `-v`, `--version` | | Print version |

### Examples

Generate a single NextSeq run (default):

```
java -jar illumina-run-simulator.jar --config config.edn
```

Generate 3 MiSeq runs:

```
java -jar illumina-run-simulator.jar --config config.edn --instrument-type miseq --num-runs 3
```

Generate a run for a specific instrument, writing to a custom directory:

```
java -jar illumina-run-simulator.jar --config config.edn --instrument-id M00456 --output-dir /path/to/output
```

## Configuration
This tool expects an [edn](https://github.com/edn-format/edn)-formatted config file:

```edn
{:instruments [{:instrument-id "M00123"
                :output-dir "test_output/M00123/22"
                :output-dir-structure :old
                :instrument-type :miseq
                :starting-run-number 300}
               {:instrument-id "M00456"
                :output-dir "test_output/M00456/22"
                :output-dir-structure :new
                :instrument-type :miseq
                :starting-run-number 256}
               {:instrument-id "VH00123"
                :output-dir "test_output/VH00123/22"
                :instrument-type :nextseq
                :starting-run-number 12}]
 :projects [{:name "mysterious_experiment"
             :species {"SARS-CoV-2" 1.0}}
            {:name "routine_testing"
             :species {"SARS-CoV-2" 0.5 "RSV" 0.3 "Mpox" 0.2}
             :cross-contamination-rate 0.02}
            {:name "quality_check"
             :species {"SARS-CoV-2" 1.0}}
            {:name "viral_outbreak"
             :species {"Mpox" 1.0}}
            {:name "assay_development"
             :species {"RSV" 1.0}}]
 :reads-per-sample 100
 :starting-plate-number 100
 :starting-date "2022-06-01"
 :mark-upload-complete true}
```

### Config fields

| Field | Description |
|-------|-------------|
| `:instruments` | List of instrument definitions. Each has an `:instrument-id`, `:output-dir`, `:instrument-type` (`:miseq`, `:nextseq`, or `:i100`), and `:starting-run-number`. MiSeq instruments also accept `:output-dir-structure` (`:old` or `:new`). |
| `:projects` | List of sequencing projects. Each has a `:name` and a `:species` map of `{species-name weight}`. Species names must match entries in the bundled reference genome registry. Weights control how often each species is assigned as the primary species for a sample. Optional `:cross-contamination-rate` (0.0–1.0) controls the fraction of reads drawn from other species in the project. |
| `:reads-per-sample` | Number of read pairs to generate per sample. |
| `:starting-plate-number` | Plate numbering starts here and increments across runs. |
| `:starting-date` | Simulated run dates start from this ISO date and advance by 1-10 days between runs. |
| `:mark-upload-complete` | If `true`, creates an `upload_complete.json` marker file in each run directory. |
| `:mark-qc-check-complete` | If `true`, creates a `qc_check_complete.json` marker file in each run directory. |

### Bundled reference genomes

The simulator includes reference genomes for three species:

| Species | Accession | Size |
|---------|-----------|------|
| SARS-CoV-2 | NC_045512.2 | ~30 kb |
| Mpox | NC_063383.1 | ~197 kb |
| RSV | NC_001781.1 | ~15 kb |

Each project in the config specifies which species are sequenced using a weighted map. Each sample is assigned a primary species by weighted random draw, and most reads come from that species' reference. If `:cross-contamination-rate` is set, a fraction of reads are drawn from other species in the project. Projects with an empty or omitted `:species` map generate purely random sequence data.

## Output

### FASTQ files

Under the FASTQ output sub-directory for each run, gzip-compressed paired-end FASTQ files are generated for each sample (`*_R1_001.fastq.gz` and `*_R2_001.fastq.gz`). Reads are derived from the bundled reference genomes with a position-dependent quality profile (logistic error model) and realistic base substitution errors.

### SampleSheets

At the top level of each run directory (and in analysis subdirectories for NextSeq/iSeq), a `SampleSheet.csv` is created. The samples listed in the SampleSheet are consistent with the FASTQ filenames. All sample identifiers are randomly generated.

Each sample is assigned to one of the projects listed in the config.

### Directory structure

After generating runs across multiple instruments, the output directory structure looks like:

```
├── M00123
│   └── 22
│       ├── 220601_M00123_300_000000000-9231O
│       │   └── Data
│       │       └── Intensities
│       │           └── BaseCalls
│       ├── 220602_M00123_301_000000000-J714H
│       │   └── Data
│       │       └── Intensities
│       │           └── BaseCalls
│       └── 220605_M00123_302_000000000-52069
│           └── Data
│               └── Intensities
│                   └── BaseCalls
├── M00456
│   └── 22
│       ├── 220603_M00456_256_000000000-1EK2R
│       │   └── Alignment_1
│       │       └── 20220603_122723
│       │           └── Fastq
│       └── 220604_M00456_257_000000000-M21I1
│           └── Alignment_1
│               └── 20220604_222848
│                   └── Fastq
└── VH00123
    └── 22
        └── 220607_VH00123_12_AAA41Q8DG
            └── Analysis
                └── 1
                    └── Data
                        └── fastq
```

## Logging

Logging uses [taoensso/telemere](https://github.com/taoensso/telemere) for structured output. Each event has a namespaced ID and a data map:

```
2022-06-16T16:14:21.524Z INFO LOG :init/loaded-reference-genomes
   data: {:num-references 3}
2022-06-16T16:14:21.526Z INFO LOG :run/created-output-dir
   data: {:run-id "220601_M00123_300_000000000-9231O", :run-output-dir "test_output/M00123/22/220601_M00123_300_000000000-9231O"}
2022-06-16T16:14:21.526Z INFO LOG :run/created-samples
   data: {:run-id "220601_M00123_300_000000000-9231O", :num-samples 93}
2022-06-16T16:14:21.528Z INFO LOG :run/created-samplesheet-file
   data: {:run-id "220601_M00123_300_000000000-9231O", :samplesheet-file "test_output/M00123/22/220601_M00123_300_000000000-9231O/SampleSheet.csv"}
2022-06-16T16:14:21.529Z INFO LOG :run/created-fastq-subdir
   data: {:run-id "220601_M00123_300_000000000-9231O", :fastq-subdir "test_output/M00123/22/220601_M00123_300_000000000-9231O/Data/Intensities/BaseCalls"}
2022-06-16T16:14:21.533Z INFO LOG :run/created-fastq-files
   data: {:run-id "220601_M00123_300_000000000-9231O", :fastq-subdir "test_output/M00123/22/220601_M00123_300_000000000-9231O/Data/Intensities/BaseCalls", :num-fastq-files 186}
```

## Development

Run tests:

```
clojure -X:test
```

Build uberjar:

```
clojure -T:build uber
```
