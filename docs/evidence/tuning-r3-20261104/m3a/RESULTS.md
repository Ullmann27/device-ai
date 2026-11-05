# R3-M3A: rejected two-pass token assembly

**Decision: NOT_ADOPTED.** Functional checks passed (24/24), but the frozen matrix contains additional allocation for empty inputs and the learned single-character case, plus CPU regressions. Original tokenization is restored before the follow-up. M1/M2 remain adopted.

| Case | Allocated bytes: baseline → candidate | CPU microseconds: baseline → candidate | CPU higher pairs |
| --- | ---: | ---: | ---: |
| token-bytes-empty | 424.000 → 456.000 | 0.383 → 0.490 | 7/9 |
| token-bytes-sentence | 3480.000 → 2888.000 | 4.106 → 3.387 | 1/9 |
| token-bytes-single | 848.000 → 816.000 | 1.804 → 1.667 | 1/9 |
| token-bytes-unicode | 75456.000 → 52000.000 | 50.083 → 58.645 | 8/9 |
| token-bytes-unique-word | 2304.000 → 2016.000 | 2.276 → 1.960 | 3/9 |
| token-merged-empty | 392.000 → 456.000 | 0.098 → 0.123 | 5/9 |
| token-merged-sentence | 3104.000 → 2824.000 | 1.296 → 1.791 | 8/9 |
| token-merged-single | 776.000 → 816.000 | 0.331 → 0.601 | 9/9 |
| token-merged-unicode | 55832.000 → 49584.000 | 38.757 → 39.030 | 5/9 |
| token-merged-unique-word | 2160.000 → 2038.080 | 2.094 → 2.283 | 5/9 |

All ten cases and 180 raw samples are retained. Exactly one process; no repeat or dropped sample. The full candidate source and patch are archived. The report retains its original phase name `m3` from execution; `m3a` is the archive directory distinguishing this rejected candidate from the follow-up.

The candidate first collects all cached arrays, counts their lengths, then copies them. This creates another temporary list and multiple passes. That source-level overhead motivates one bounded follow-up; it is not claimed to be an isolated proof of the measured CPU cause. The follow-up uses a single-pass primitive buffer with geometric growth, checked integer bounds, direct array copies and no returned cache alias. The same ten fixtures, warmups and nine pairs remain frozen. No empty-only special case, empirical prefix threshold or favorable rerun is introduced.
