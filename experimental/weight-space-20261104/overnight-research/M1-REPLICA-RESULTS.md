# Milestone 1: widened channels diverged during training

Actual frozen checkpoint bytes were read from the published 51,103,436-byte
archive. All four model hashes, body checksums, finite tensors and layouts
passed the pinned reader. Archive and tokenizer hashes were unchanged after
the measurement. No data split, optimizer or model runtime was executed.

## Measured result

W0's 32,768 embedding groups and 64 final-gain groups are exact eightfold
replicas. For each dense matrix, summing its eight input replicas cancels the
paired initialization noise up to stored Float32 rounding. Across the twelve
dense matrices, relative RMS differences between resulting output replicas
range from **3.0670e-9 to 4.7912e-9**.

In W1, all 32,768 embedding groups and all 64 final-gain groups contain
different stored values. Every corresponding group of the twelve collapsed
dense matrices also differs. Their relative RMS differences range from
**0.00130778 to 0.00674159** (approximately **0.13–0.67%**).

This rejects exact replica preservation at the selected trained W1 checkpoint.
It does not measure functional rank, effective parameters or additional
knowledge. Functional activation measurements are the next milestone.

The full tensor-level measurements are in `replica-audit.json`; the report
does not select favorable tensors. Parameters remain 132,160 for S0/S1 and
6,562,304 for W0/W1. Pure FP32 storage remains 528,640 and 26,249,216 bytes.
Neither the failed capacity result nor final quality scores are changed.

## Verification

Ten metric tests passed, including a hand-calculated variance, signed zero,
exact replicas, paired-noise cancellation, a cancellation-breaking update,
zero scale and invalid/nonfinite inputs. The initial test execution failed
because the implementation file had not yet been written; this is a new
implementation sequence, not a reproduction of an old model bug.

`audit_replicas.py` binds the original widening/training/format sources at
`59891cfad6dd015f47c187cc688ea94b0026ad7d` and the previously independently
verified binary reader. It refuses existing output paths. The audit JSON
records its own source hash, protocol hash and tool versions.

Reproduce from the repository root, using the actual preserved input files:

```sh
python3 experimental/weight-space-20261104/overnight-research/audit_replicas.py \
  --archive /path/to/LM1_Training_Research_20261104.zip \
  --tokenizer /path/to/lm1-tokenizer.lmbp \
  --output /new/path/replica-audit.json
```

New inference remains **NOT_RUN** at this milestone. The first harness build
exposed Kotlin's internal kernel boundary; the harness was adjusted to call
the existing public `nextDistribution` API. No production source changed.
