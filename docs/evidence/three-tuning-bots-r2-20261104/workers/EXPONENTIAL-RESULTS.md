# Final exponential dependency lookup: complete host results

Status: **RETURNED_TO_R1 / CANDIDATES_NOT_ADOPTED**. Root declined the general helper change because the complete comparison retains control regressions, and restored the R1 production source. No third candidate or further measurement was performed.

Same frozen 40 fixtures and nine paired rounds as the archived binary candidate. The one authorized recovery attempt completed with exit 0 after the prior host-load abort; both attempts remain archived. The timing phase observed one-minute host load 6.85–7.09. These are synthetic Node host selector measurements, not Android/Fold7 or overall-worker results.

Times are separate medians in microseconds per selection. Factor is the median of paired baseline/candidate ratios, so it need not equal the ratio of the separate medians. Values below 1 favor the baseline. Every raw sample and quartile is in `benchmark-exponential.json`.

| Tasks | Case | R1 baseline µs | Candidate B µs | Paired factor | Task-ID reads before → after |
|---:|---|---:|---:|---:|---:|
| 0 | none | 0.0226 | 0.0288 | 0.937 | 0 → 0 |
| 0 | none-no-match | 0.0235 | 0.0241 | 0.997 | 0 → 0 |
| 0 | one-first | 0.0375 | 0.0388 | 0.951 | 0 → 0 |
| 0 | one-middle | 0.0379 | 0.0384 | 1.007 | 0 → 0 |
| 0 | one-last | 0.0385 | 0.0390 | 0.988 | 0 → 0 |
| 0 | sixteen-spread | 0.0382 | 0.0380 | 1.017 | 0 → 0 |
| 0 | sixteen-first-blocked | 0.0382 | 0.0375 | 1.023 | 0 → 0 |
| 0 | sixteen-last-blocked | 0.0389 | 0.0376 | 1.011 | 0 → 0 |
| 1 | none | 0.0824 | 0.0878 | 1.043 | 0 → 0 |
| 1 | none-no-match | 0.0910 | 0.0820 | 1.042 | 0 → 0 |
| 1 | one-first | 0.0800 | 0.0813 | 0.992 | 0 → 0 |
| 1 | one-middle | 0.0786 | 0.0737 | 1.035 | 0 → 0 |
| 1 | one-last | 0.0734 | 0.0745 | 0.993 | 0 → 0 |
| 1 | sixteen-spread | 0.0746 | 0.0749 | 1.017 | 0 → 0 |
| 1 | sixteen-first-blocked | 0.0737 | 0.0747 | 0.985 | 0 → 0 |
| 1 | sixteen-last-blocked | 0.0762 | 0.0767 | 0.994 | 0 → 0 |
| 20 | none | 0.9356 | 0.9247 | 1.018 | 0 → 0 |
| 20 | none-no-match | 0.6732 | 0.8693 | 0.935 | 0 → 0 |
| 20 | one-first | 1.6654 | 1.5341 | 1.094 | 19 → 19 |
| 20 | one-middle | 4.2628 | 6.1476 | 0.712 | 190 → 171 |
| 20 | one-last | 7.9866 | 5.8257 | 1.367 | 380 → 152 |
| 20 | sixteen-spread | 11.9611 | 13.7534 | 0.932 | 656 → 408 |
| 20 | sixteen-first-blocked | 0.6286 | 0.5673 | 1.113 | 4 → 4 |
| 20 | sixteen-last-blocked | 13.8106 | 13.7869 | 1.002 | 656 → 408 |
| 100 | none | 4.6445 | 4.5171 | 1.009 | 0 → 0 |
| 100 | none-no-match | 4.9136 | 4.9446 | 0.994 | 0 → 0 |
| 100 | one-first | 9.0586 | 7.4970 | 1.221 | 99 → 99 |
| 100 | one-middle | 99.6938 | 47.1328 | 2.152 | 4,950 → 1,287 |
| 100 | one-last | 195.5662 | 51.1488 | 3.835 | 9,900 → 1,386 |
| 100 | sixteen-spread | 1360.7863 | 500.1486 | 2.721 | 70,896 → 15,456 |
| 100 | sixteen-first-blocked | 14.4223 | 22.3031 | 0.646 | 504 → 588 |
| 100 | sixteen-last-blocked | 696.1094 | 261.4652 | 2.578 | 70,896 → 15,456 |
| 500 | none | 14.9642 | 14.5986 | 1.066 | 0 → 0 |
| 500 | none-no-match | 21.8992 | 23.1941 | 1.006 | 0 → 0 |
| 500 | one-first | 27.8478 | 21.8007 | 1.278 | 499 → 499 |
| 500 | one-middle | 1423.6807 | 153.8824 | 9.075 | 124,750 → 8,483 |
| 500 | one-last | 2797.9411 | 171.6240 | 16.172 | 249,500 → 8,982 |
| 500 | sixteen-spread | 26496.3624 | 2499.5468 | 10.792 | 2,054,096 → 123,420 |
| 500 | sixteen-first-blocked | 190.3759 | 93.7909 | 1.767 | 15,004 → 4,840 |
| 500 | sixteen-last-blocked | 29952.2736 | 2709.9640 | 11.436 | 2,054,096 → 123,420 |

At sizes 0 and 1 all dependency-bearing case names denote degenerate no-dependency controls: a valid inter-task dependency cannot exist. At 20/100/500, `one-first`, `one-middle`, and `one-last` each have one completed dependency shared by the remaining READY tasks. The sixteen-dependency cases spread 16 dependencies over the document; the blocked cases leave the first/last dependency PROPOSED. State-read order matched the original in every instrumented case.

The original index-zero regression is removed in this matrix, and large late/shared-dependency cases improve. However, at 20 tasks `one-middle` regresses from 4.2628 to 6.1476 µs (paired factor 0.712), and at 100 tasks `sixteen-first-blocked` regresses from 14.4223 to 22.3031 µs (factor 0.646; 504 → 588 ID reads). Both are slower in 9/9 pairs. At 20 tasks `sixteen-spread` also has a paired factor 0.932 and is slower in 7/9 pairs. No-dependency controls are generally near neutral, with noisy small-case ratios and no universal improvement claim.

For 500 tasks / 16 spread dependencies, task-ID reads drop from 2,054,096 to 123,420, while the paired timing factor is 10.792. Those gains are not a weighted whole-worker benefit: real workload frequencies were not measured. Absolute times must not be compared directly between the separate binary and exponential runs, which had different host/JIT conditions.

No total allocation, GC, battery, thermals, language-model quality, native scheduling or cancellation timing was measured. Functional tests and parity are separate evidence: exact baseline and B each passed 59/59 tests; source-bound parity passed 960 selection, 27 between-call mutation, 19 operation/validation and 219 position-boundary cases.
