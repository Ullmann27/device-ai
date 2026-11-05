# R3-M1: row-sized NLL scratch

**Decision: ADOPTED_IN_DRAFT.** All 24 relevant test methods pass, including seven new source-bound comparison methods. Four multi-token workloads have substantially lower allocation; the four one-token controls require the same scratch size. No general CPU-speed claim is made.

Reference: published R2/R1-retained source `73b64ec299fd314dfdf07835fc7d8fc0d74b51a2`, exact six-file source hashes in `manifest.json`. Measured candidate source hashes match the production files in this milestone commit. The old four-argument softmax signature remains; the new internal overload changes only output addressing. Floating-point expression and summation order remain unchanged.

| Case | Allocated bytes per call: baseline → candidate | CPU microseconds: baseline → candidate | Candidate CPU higher pairs |
| --- | ---: | ---: | ---: |
| nll-MINGRU-v2048-t1 | 20280.000 → 20248.000 | 50.144 → 52.007 | 6/9 |
| nll-MINGRU-v2048-t64 | 1100056.000 → 583928.000 | 2614.480 → 2638.896 | 4/9 |
| nll-MINGRU-v258-t1 | 6032.000 → 6032.000 | 12.131 → 12.152 | 3/9 |
| nll-MINGRU-v258-t16 | 48344.000 → 32864.000 | 116.041 → 112.968 | 2/9 |
| nll-TRANSFORMER-v2048-t1 | 21296.000 → 21168.000 | 55.437 → 52.522 | 4/9 |
| nll-TRANSFORMER-v2048-t64 | 1170448.000 → 654352.000 | 2210.276 → 2156.662 | 2/9 |
| nll-TRANSFORMER-v258-t1 | 7712.000 → 7712.000 | 35.290 → 34.094 | 3/9 |
| nll-TRANSFORMER-v258-t16 | 54560.000 → 39072.000 | 132.044 → 131.799 | 4/9 |

At 64 tokens and vocabulary 2,048, scratch capacity falls from 131,072 to 2,048 floats: exactly 516,096 fewer payload bytes. The Transformer complete-call allocation median falls by that same amount; the minGRU measurement has an additional 32-byte difference attributable only to measured runtime allocation, not claimed as a source-level saving.

The largest CPU-median increase is the one-token minGRU V2048 control (50.144 → 52.007 microseconds; six of nine pairs higher). The identical-source control for that case already had seven of nine higher and a larger relative difference. Long minGRU has a small CPU-median increase with only four of nine higher. These observations do not establish either a CPU improvement or a device regression; all samples remain.

Exactly one A/A process covered all 26 frozen workloads before edits, and one candidate process covered the eight NLL workloads with nine alternating pairs each. No rerun or environmental abort occurred. Output checksums agree in every pair. Allocation is created heap memory per complete NLL invocation, not retained memory or Android peak RAM.

Correctness includes 96 NLL cases, 40 next-distribution cases, existing softmax offsets/aliasing/special values, invalid outcomes, unchanged parameters and cache/output isolation, Unicode and concurrent readers. Existing full-operation/end-to-end gradient, causality, checkpoint, deterministic toy-training and 10 sampling regressions also pass. The toy-training regression is not a project-model training job.

Reproduce from this published milestone checkout using `tools/tuning-r3-20261104/run.py check` and then `measure --group nll --phase m1`, with a new external output directory and the same available compiler dependencies. The runner resolves the immutable public baseline first and enforces its tree. Do not silently compare a later candidate under this milestone label.

Rollback: restore only this milestone’s `nll` and softmax diff; R1 sampler and all archived R2 evidence stay intact. Android/ART, Fold7, whole-app quality, battery and peak resident memory remain NOT_RUN.
