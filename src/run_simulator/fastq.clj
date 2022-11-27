(ns run-simulator.fastq)

;; illumina fastq header:
;; @<instrument>:<run number>:<flowcell ID>:<lane>:<tile>:<x-pos>:<y-pos> <read>:<is filtered>:<control number>:<index>
;; where:
;; <read> is 1 for R1, 2 for R2
;; <is filtered> is N if not filtered, Y if is filtered
;; <control number> is usually 0
;; <index> index of read (may be numeric)

(defn bcl->fastq
  ""
  []
  )
  
