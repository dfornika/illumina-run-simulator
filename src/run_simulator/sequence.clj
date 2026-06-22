(ns run-simulator.sequence
  )


(defn random-seq
  "Generate a random DNA sequence of the given length (uniform A/C/G/T)."
  [len]
  (apply str (repeatedly len #(rand-nth [\A \C \G \T]))))


(defn reverse-complement
  "Return the reverse complement of a DNA sequence. Handles upper/lowercase and N."
  [s]
  (let [base-complement {\A \T
                         \C \G
                         \G \C
                         \T \A
                         \a \t
                         \c \g
                         \t \a
                         \g \c
                         \N \N
                         \n \n}]
    (apply str (map base-complement (reverse s)))))


(defn random-subseq
  "Take a random subseq of length `len` from s.
   If `len` greater than length of s, returns the full sequence.

   Returns: dict with keys :subseq, :start-index-inclusive, :end-index-exclusive,
   where `(subs s start-index-inclusive end-index-exclusive)` should return the
   same subseq.
  "
  [s len]
  (let [seq-length (count s)
        start (if (> len seq-length)
                0
                (rand-int (- seq-length len)))
        end (if (> len seq-length)
              seq-length
              (+ start len))]
      {:subseq (subs s start end)
       :start-index-inclusive start
       :end-index-exclusive end}))


(defn reads-from-seq
  "Extract paired-end reads: R1 from the start, R2 as reverse complement from the end."
  [s len]
  (let [seq-length (count s)
        r1 (subs s 0 len)
        r2 (reverse-complement (subs s (- seq-length len) seq-length))]
    {:r1 r1
     :r2 r2}))


(defn flat-error-profile
  "Constant error probability regardless of read position."
  [x n]
  n)


(defn logistic-error-profile
  "Generate probability of error based on position in read, using logistic function
   https://en.wikipedia.org/wiki/Logistic_function

   Sensible parameters:
   L: 0.1
   k: 0.05
   x0: 200

   Increasing L increases max value.
   Increasing k increases steepness of increase.
   Increasing x0 shifts inflection point to the right.
  "
  [x L k x0]
  (let [exponent (* (* -1 k) (- x x0))]
    (+ 0.0001 (/ L (+ 1 (Math/exp exponent))))))


(defn probability->phred
  "Convert error probability to PHRED quality score: round(-10 * log10(p))."
  [prob-error]
  (Math/round (* -10 (Math/log10 prob-error))))


(defn phred->char
  "Convert PHRED score to ASCII quality character (PHRED+33 encoding)."
  [phred]
  (char (+ phred 33)))


(defn generate-quality-scores
  "Generate a vector of PHRED quality scores for a read, using a logistic error profile with per-position jitter."
  [read-length {:keys [L k x0] :or {L 0.1 k 0.05 x0 200}}]
  (mapv (fn [pos]
          (let [base-prob (logistic-error-profile pos L k x0)
                jittered (max 0.00001 (min 1.0 (+ base-prob (* base-prob (- (rand) 0.5) 0.3))))
                phred (probability->phred jittered)]
            (max 2 (min 41 phred))))
        (range read-length)))


(defn quality-scores->string
  "Convert a vector of PHRED scores to a FASTQ quality string."
  [scores]
  (apply str (map phred->char scores)))


(defn introduce-errors
  "Randomly substitute bases at positions where quality is low, consistent with PHRED error probabilities."
  [seq-str quality-scores]
  (apply str
         (map (fn [base phred]
                (let [error-prob (Math/pow 10.0 (/ (- phred) 10.0))]
                  (if (< (rand) error-prob)
                    (rand-nth (vec (disj #{\A \C \G \T} base)))
                    base)))
              seq-str quality-scores)))
