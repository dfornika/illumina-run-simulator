(ns run-simulator.sequence
  )


(defn reverse-complement
  ""
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
  ""
  [s len]
  (let [seq-length (count s)
        r1 (subs s 0 len)
        r2 (reverse-complement (subs s (- seq-length len) seq-length))]
    {:r1 r1
     :r2 r2}))


(defn flat-error-profile
  ""
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
  [prob-error]
  (Math/round (* -10 (Math/log10 prob-error))))


(defn phred->char
  [phred]
  (char (+ phred 33)))
