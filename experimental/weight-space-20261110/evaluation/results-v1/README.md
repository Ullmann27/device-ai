# Direct weight replay: PASS

Executed once after registration was published in commit
`81aeb120ec5cd7ca885c966fa9c2cc50862aeec7` (PR #728).
All registered source, executable and runtime hashes matched before launch.
Exact command, hashes and process accounting: [receipt.json](receipt.json).
Raw output: [stdout.txt](stdout.txt); stderr is empty.

**16 checks passed, zero failed. Exactly 80 optimizer updates on the existing
2,632-parameter software fixture.** Elapsed wall time 0.385894498 seconds;
Linux maximum child RSS 68,792 KiB. These are host measurements.

| Comparison | Changed numeric weights | Maximum absolute delta |
|---|---:|---:|
| Identity metadata only | 0 | 0 |
| Step counter only | 0 | 0 |
| Initial to uninterrupted step 24 | 2,632 | 0.01751232147216797 |
| Saved prefix to restored prefix | 0 | 0 |
| Uninterrupted to complete resume | 0 | 0 |
| Uninterrupted to weights-only resume | 2,632 | 0.01369890570640564 |
| Uninterrupted to moment reset | 2,632 | 0.010761678218841553 |

Changed Float32-bit counts equal the numeric counts in this run. Complete
resume also reproduces the complete training-state file identity. The saved
prefix remains unchanged and contains nonzero first/second moments.

Both negative controls reproduce the historical comparison's false-positive
mechanism: the complete-state hash differs even though every parameter bit is
identical. The new tensor comparison rejects both as weight changes. The
historical source and evidence remain unmodified.

The fixture's `sourceSha256` value consisting of 64 ones is a symbolic test
identity. It is not a real source digest. Actual source and tool identities are
bound by the published [build registration](../build-registration.json).

This run establishes numerical continuity and direct parameter changes. It
does not measure language quality, a held-out task, 16M continuation, Android
behavior, or productive model integration. No model was promoted.
