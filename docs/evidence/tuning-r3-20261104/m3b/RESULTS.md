# R3-M3B: single-pass primitive token assembly

**Decision: ADOPTED_IN_DRAFT.** All 24 methods pass. The single-pass candidate has lower allocation medians in all ten frozen cases, including the controls that regressed in M3A. M1/M2 remain unchanged. Separate caller/runtime and archive-route verification follows; no Android acceptance is claimed.

| Case | Allocated bytes: baseline → candidate | CPU microseconds: baseline → candidate | CPU higher pairs | Median paired CPU delta (microseconds) |
| --- | ---: | ---: | ---: | ---: |
| token-bytes-empty | 424.000 → 368.000 | 0.461 → 0.376 | 2/9 | -0.060 |
| token-bytes-sentence | 3648.000 → 3072.000 | 5.838 → 3.911 | 0/9 | -2.592 |
| token-bytes-single | 816.000 → 704.000 | 1.500 → 0.958 | 0/9 | -0.435 |
| token-bytes-unicode | 77776.000 → 60192.000 | 65.296 → 52.897 | 1/9 | -12.640 |
| token-bytes-unique-word | 2352.000 → 2112.000 | 2.254 → 1.694 | 1/9 | -0.568 |
| token-merged-empty | 392.000 → 368.000 | 0.127 → 0.080 | 1/9 | -0.047 |
| token-merged-sentence | 3104.000 → 2816.000 | 2.558 → 2.323 | 1/9 | -0.084 |
| token-merged-single | 792.000 → 704.000 | 0.667 → 0.616 | 1/9 | -0.259 |
| token-merged-unicode | 55832.000 → 50768.000 | 34.030 → 28.835 | 1/9 | -2.900 |
| token-merged-unique-word | 2160.000 → 2006.080 | 1.475 → 1.713 | 4/9 | -0.089 |

The allocation reduction is approximately 6.1–22.6% across these cases. Nine separate CPU medians are lower. The learned unique-word control has a higher separate median (1.475 → 1.713 microseconds), but only four of nine paired deltas are positive. The complete paired statistics are retained; this is not a universal CPU-speed claim.

One primitive buffer starts at the input UTF-16 length. Each cached token array is copied once into that buffer. Geometric growth handles multibyte expansion; long arithmetic caps growth and a checked required length rejects integer overflow. The result is trimmed only when necessary. It never returns a cached word array. Pretokenization, merges, vocabulary bytes, cache access order and synchronization remain unchanged.

Exactly one follow-up process used the original ten fixtures, identical fixture hashes, warmups, batch counts and nine pairs. All 180 samples and checksums are complete. There was no abort, timing repeat or third tokenizer candidate. M3A remains explicitly rejected with its full source, original runner and raw results; absolute timings across A and B processes are not directly comparable.

Tests include 506 Unicode/byte/learned-tokenizer fixture comparisons before/after cache hits, returned-array mutation checks, fresh deserialization, 48 concurrent reader tasks, all preceding inference parity and the existing gradient/checkpoint/sampling regressions. No tokenizer corpus or learned merge bytes changed.

Reproduce from this published milestone checkout with a new external output directory, `run.py check`, then `measure --group tokenizer --phase m3b`. The final runner also supports `check --candidate m3a` to explicitly compile the rejected archive with all six expected source hashes enforced; this never substitutes active B for archived A. Only functional archive verification is scheduled for the current round, with no further timing.

Rollback only this `encode` diff to retain M1/M2 and R1. Allocated bytes are not retained cache size, peak RSS, Android latency or language-model quality.
