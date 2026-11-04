# R3-M4: direct hexadecimal encoding

**Decision: ADOPTED_IN_DRAFT.** All 26 methods pass, including two new hex-specific methods. Allocation and CPU medians are lower in all six frozen cases. No candidate CPU sample exceeds its paired reference sample. This is the representation helper only; the SHA-256 algorithm and checkpoint validation have not changed. Final runtime/caller checks follow separately.

| Case | Allocated bytes: baseline → candidate | CPU microseconds: baseline → candidate | CPU higher pairs | Median paired CPU delta (microseconds) |
| --- | ---: | ---: | ---: | ---: |
| hex-1024 | 678576.000 → 6200.000 | 1971.972 → 3.846 | 0/9 | -1968.126 |
| hex-all-bytes | 169744.000 → 1592.000 | 487.305 → 1.062 | 0/9 | -486.075 |
| hex-empty | 96.000 → 0.000 | 0.229 → 0.073 | 0/9 | -0.156 |
| hex-ff | 760.000 → 72.000 | 2.094 → 0.091 | 0/9 | -2.005 |
| hex-sha256 | 21296.000 → 248.000 | 60.583 → 0.176 | 0/9 | -60.446 |
| hex-zero | 760.000 → 72.000 | 3.598 → 0.101 | 0/9 | -3.505 |

For the 32-byte digest case, allocation decreases from 21,296 to 248 bytes per conversion (98.84%). The CPU medians are approximately 60.583 and 0.176 microseconds, respectively. These are single-process JVM component observations; no end-to-end speedup, device gain or statistical population estimate is inferred.

The candidate replaces one Formatter invocation per byte with two lowercase nibble lookups in a character array. Signed bytes are masked to 0–255. Empty input returns the empty string. Multiplication uses long arithmetic before allocating a checked result length; no enormous overflow-boundary array was allocated in tests. The returned character sequence and every checkpoint/vocabulary hash string remain equal to the frozen reference in the tested cases. Source SHA-256 receipts bind the exact six candidate and six original source files.

The new checks cover all 256 byte values, zero/0xff, 33 seeded random arrays across 11 lengths up to 4,096 bytes, unchanged inputs, output width/alphabet, and default locales de-AT/en-US/tr-TR/ar-EG/ja-JP. Locale defaults are restored after the test. The existing 24 inference/tokenizer/gradient/checkpoint/sampling methods also pass.

The six-case matrix was committed before measurement (public `40ba0479717c91a206fd55f8134fd610b3c077b2`). Four alternating warmup batches and nine pairs are retained in each process. The A/A control uses the same unmodified hex implementation in both packages while earlier unrelated R3 changes remain in the current graph. The separate hex benchmark source and fixture hashes are identical in A/A and candidate runs. Exactly one A/A and one candidate process completed, with 108 samples each, no abort and no repeat. All A/A allocation medians are equal; its CPU noise is preserved in `identicalSourceControl`.

Reproduce from the milestone checkout with a new external output directory: `run.py check`, then `measure --group hex --phase m4`; summarize with `--control` pointing to the saved A/A directory and `--control-group hex`. Original M1–M3 benchmark source and workloads were not edited. This is a component allocation optimization; retained memory, Android latency, battery use and model quality remain unmeasured.
