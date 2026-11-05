# R3-M2: direct next-token probability output

**Decision: ADOPTED_IN_DRAFT.** All 24 relevant test methods pass. All eight workloads allocate less memory. R3-M1 remains present; the benchmark compares this function against the same frozen R2 baseline. The forward logits are unchanged.

| Case | Allocated bytes: baseline → candidate | CPU microseconds: baseline → candidate | CPU higher pairs | Median paired CPU delta (microseconds) |
| --- | ---: | ---: | ---: | ---: |
| distribution-MINGRU-v2048-t1 | 28560.000 → 20328.000 | 37.510 → 35.847 | 7/9 | +1.647 |
| distribution-MINGRU-v2048-t64 | 1107848.000 → 583480.000 | 839.871 → 856.962 | 3/9 | -45.343 |
| distribution-MINGRU-v258-t1 | 7080.000 → 6032.000 | 11.376 → 9.030 | 1/9 | -2.165 |
| distribution-MINGRU-v258-t16 | 49248.000 → 32715.500 | 100.826 → 62.340 | 0/9 | -36.789 |
| distribution-TRANSFORMER-v2048-t1 | 29504.000 → 21320.000 | 55.733 → 54.842 | 3/9 | -1.145 |
| distribution-TRANSFORMER-v2048-t64 | 1178256.000 → 653952.000 | 1201.524 → 1091.388 | 4/9 | -5.420 |
| distribution-TRANSFORMER-v258-t1 | 8760.000 → 7712.000 | 39.689 → 35.199 | 2/9 | -4.490 |
| distribution-TRANSFORMER-v258-t16 | 55768.000 → 39192.000 | 87.888 → 89.350 | 4/9 | -2.651 |

The returned row is now the direct softmax destination. The old sequence-sized scratch and final-row copy are removed. At T64/V2048 this eliminates one 131,072-float array (524,304 bytes including its measured HotSpot header/alignment). The Transformer complete-call allocation difference is exactly 524,304 bytes. One-token controls also benefit because the old implementation allocated two vocabulary-sized arrays.

Separate CPU medians and paired deltas answer different questions and all are retained. The minGRU V2048/T1 case has a lower separate candidate median while seven of nine paired deltas are positive; this is not presented as a reliable speed improvement. Two other separate medians rise slightly (Transformer T16 and minGRU T64), with only four/three pairs higher. No general CPU, Android or model-quality improvement is claimed.

Exactly one complete candidate process: eight cases, nine alternating pairs, 144 raw sample records. No rerun or environmental abort. All fixture hashes equal the preregistered A/A control and all paired output checksums agree. Full equality tests compare probability bits, result ownership, unchanged parameter bytes, existing softmax signature and both architectures.

Reproduce from this published milestone checkout, using the runner with a fresh output directory, `check`, then `measure --group distribution --phase m2`; do not label a later source state as M2. Restore only this milestone’s `nextDistribution` diff to roll back, keeping M1 and R1.
