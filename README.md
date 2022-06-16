# Illumina Run Simulator
This is a helper utility designed to assist with testing and development of [BCCDC-PHL/auto-fastq-symlink](https://github.com/BCCDC-PHL/auto-fastq-symlink) and [BCCDC-PHL/illumina-uploader](https://github.com/BCCDC-PHL/illumina-uploader).

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
                :output-dir-structure :old
                :instrument-type :miseq
                :starting-run-number 300}
               {:instrument-id "M00456"
                :output-dir "test_output/M00456/22"
                :output-dir-structure :new
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
 :run-interval-ms 10000
 :mark-upload-complete true}
```

## Logging

Logs are in [JSON Lines](https://jsonlines.org/) format. Each line is a json object, with a timestamp and an event type. Additional fields vary by event type:

```json
{"timestamp":"2022-06-16T16:14:21.523984-07:00","event":"created_run_output_dir","run_id":"220601_M00123_300_000000000-9231O","run_output_dir":"test_output/M00123/22/220601_M00123_300_000000000-9231O"}
{"timestamp":"2022-06-16T16:14:21.525995-07:00","event":"created_samples","run_id":"220601_M00123_300_000000000-9231O","num_samples":93}
{"timestamp":"2022-06-16T16:14:21.528003-07:00","event":"created_samplesheet_file","run_id":"220601_M00123_300_000000000-9231O","samplesheet_file":"test_output/M00123/22/220601_M00123_300_000000000-9231O/SampleSheet.csv"}
{"timestamp":"2022-06-16T16:14:21.528502-07:00","event":"created_fastq_subdir","run_id":"220601_M00123_300_000000000-9231O","fastq_subdir":"test_output/M00123/22/220601_M00123_300_000000000-9231O/Data/Intensities/BaseCalls"}
{"timestamp":"2022-06-16T16:14:21.533077-07:00","event":"created_fastq_symlinks","run_id":"220601_M00123_300_000000000-9231O","fastq_subdir":"test_output/M00123/22/220601_M00123_300_000000000-9231O/Data/Intensities/BaseCalls","num_symlinks":186}
{"timestamp":"2022-06-16T16:14:21.533737-07:00","event":"created_upload_complete_file","run_id":"220601_M00123_300_000000000-9231O","upload_complete_file":"test_output/M00123/22/220601_M00123_300_000000000-9231O/upload_complete.json"}
```

## Output
New runs are be simulated on a regular interval, as configured in by the `:run-interval-ms` field of the config file. One of the instruments
listed in the `:instruments` list of the config file will be selected at random. The simulated run will be output into the `:output-dir` for that instrument.

### Directories
After letting the simulator run for several rounds, an output directory structure resembling this will be constructed:

```
├── M00123
│   └── 22
│       ├── 220601_M00123_300_000000000-9231O
│       │   └── Data
│       │       └── Intensities
│       │           └── BaseCalls
│       ├── 220602_M00123_301_000000000-J714H
│       │   └── Data
│       │       └── Intensities
│       │           └── BaseCalls
│       └── 220605_M00123_302_000000000-52069
│           └── Data
│               └── Intensities
│                   └── BaseCalls
├── M00456
│   └── 22
│       ├── 220603_M00456_256_000000000-1EK2R
│       │   └── Alignment_1
│       │       └── 20220603_122723
│       │           └── Fastq
│       ├── 220604_M00456_257_000000000-M21I1
│       │   └── Alignment_1
│       │       └── 20220604_222848
│       │           └── Fastq
│       ├── 220606_M00456_258_000000000-068E2
│       │   └── Alignment_1
│       │       └── 20220606_100128
│       │           └── Fastq
│       ├── 220608_M00456_259_000000000-L504P
│       │   └── Alignment_1
│       │       └── 20220608_213013
│       │           └── Fastq
│       └── 220609_M00456_260_000000000-N257Y
│           └── Alignment_1
│               └── 20220609_025056
│                   └── Fastq
└── VH00123
    └── 22
        └── 220607_VH00123_12_AAA41Q8DG
            └── Analysis
                └── 1
                    └── Data
                        └── fastq

```

### Files
Under the fastq output sub-directory for the run, a set of empty `.fastq.gz` files will be created. All simulated `.fastq.gz` files are empty.
They contain no reads, and are not valid gzip files.

At the top-level of each simulated run output directory, a simulated `SampleSheet.csv` will be created. The samples listed in the SampleSheet
should be consistent with the filenames of the simulated `fastq.gz` files. All sample identifiers are randomly-generated.

Each sample may be randomly assigned to one of the projects listed under `:projects` in the config file, or may not be assigned to a project.

Excerpt from example simulated MiSeq SampleSheet:

```
[Header]
Experiment_Name,20220502-test
Index_Adapters,IDT-Ilmn DNA-RNA UD Indexes SetA Tagmentation
Investigator_Name,
Instrument_Type,MiSeq
Workflow,GenerateFASTQ
IEMFileVersion,5
Date,5/2/2022
Application,FASTQ Only
Assay,Illumina DNA Prep
[Reads]
151
151
[Settings]
ReverseComplement,0
Adapter,CTGTCTCTTATACACATCT
[Data]
Sample_ID,Sample_Name,Sample_Plate,Sample_Well,Index_Plate_Well,I7_Index_ID,index,I5_Index_ID,index2,Sample_Project,Description
R7242146906-100-A-A01,,,,A01,UDP0001,GAACTGAGCG,UDP0001,TCGTGGAGCG,mysterious_experiment,
R4048576324-100-A-B01,,,,B01,UDP0002,AGGTCAGATA,UDP0002,CTACAAGATA,,
R2005726645-100-A-C01,,,,C01,UDP0003,CGTCTCATAT,UDP0003,TATAGTAGCT,,
```

Excerpt from simulated NextSeq SampleSheet:

```
[Header]
FileFormatVersion,2
RunName,
InstrumentPlatform,NextSeq1k2k
InstrumentType,NextSeq2000
[Reads]
Read1Cycles,151
Read2Cycles,151
Index1Cycles,10
Index2Cycles,10
[Sequencing_Settings]
LibraryPrepKits,IlluminaDNAPrep
[BCLConvert_Settings]
SoftwareVersion,3.7.4
AdapterRead1,CTGTCTCTTATACACATCT
AdapterRead2,CTGTCTCTTATACACATCT
FastqCompressionFormat,gzip
[BCLConvert_Data]
Sample_ID,Index,Index2
R5797349738-100-A-A01,GAACTGAGCG,TCGTGGAGCG
R5769150519-100-A-B01,AGGTCAGATA,CTACAAGATA
R8587059395-100-A-C01,CGTCTCATAT,TATAGTAGCT
[Cloud_Settings]
GeneratedVersion,0.10.1.202102231441
[Cloud_Data]
Sample_ID,ProjectName,LibraryName,LibraryPrepKitUrn,LibraryPrepKitName,IndexAdapterKitUrn,IndexAdapterKitName
R5797349738-100-A-A01,,,,,,
R5769150519-100-A-B01,quality_check,,,,,
R8587059395-100-A-C01,viral_outbreak,,,,,
```
