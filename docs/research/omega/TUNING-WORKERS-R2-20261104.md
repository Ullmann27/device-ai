# Tuning bot 3, round 2 — final handoff

**RETURNED_TO_R1 / CANDIDATES_NOT_ADOPTED.** Root rejected both helper changes after the complete comparisons. Active `.github/continuum/development-backlog.mjs` is byte-identical to the R1 / R2-base source (SHA-256 `d0cb68413ff89063b685395c0e633a10c04f038e8329658c085ce0c8748f3a1d`). The accepted R1 `nextEligible` optimization remains intact.

Logical base `db3295d51c769afdaf809ac1dfae2ada84a3274f`; public identical tree at `04b46f125d1086e6ea540d3c68700407f340f607`; own branch `tuning/workers-r2-59ad`. Existing EXP-01/EXP-11 contracts remain unchanged. No schema, authority, lock, lease, evidence, cancellation, offline or execution feature changed.

Candidate A used pure binary dependency lookup. Candidate B used exponential range expansion from index zero followed by binary search. Their exact full sources, patches and hashes remain archived. A's first-position regression was strong; B fixed that control but retained two material planned disadvantages in all nine pairs: 20-task `one-middle` (factor 0.712) and 100-task `sixteen-first-blocked` (factor 0.646). Large late/dense dependencies improved, but actual workload weighting is unknown. Neither candidate is adopted.

Original and B each passed **59/59 tests**. All seven new regression tests remain. Full source-bound parity passed **960 selector + 27 between-call mutation + 19 operation/validation + 219 position-boundary cases**. The initial missing-fixture environment attempt is preserved separately; only the small versioned fixture directory was added to the own sparse checkout.

A and B each have complete **40-case / 360-pair** timing records. Exactly one environment-related B attempt aborted with exit 1 at load 17.8 > 9 and zero stdout bytes; its stderr and receipt remain unchanged. Exactly one explicitly authorized recovery of the identical frozen B source/protocol completed with exit 0, zero stderr and timing load 6.85–7.09. No third candidate, changed guard or further measurement was performed. No Android/Fold7 result is claimed.

The reproduction runner requires an explicit archive choice and enforces its hash, so restoring R1 cannot silently substitute active code for B:

```sh
node tools/tuning-workers-r2-20261104/benchmark.mjs --candidate=binary --parity-only
node tools/tuning-workers-r2-20261104/benchmark.mjs --candidate=exponential --parity-only
```

Public-first baseline selection and fallback tree/hash checks remain unchanged. Measured harness snapshots are archived; the timing/counting tail and 40-fixture module remain byte-identical. After restoration the bot verified source/archives, syntax and Git only. Root owns the independent final Node and archive-route functional checks; no further timing is authorized here.

Full report, all tables, raw samples and reproduction: [workers/README.md](../../evidence/three-tuning-bots-r2-20261104/workers/README.md). Root publishes the final archive/test snapshot. No direct push, main/production merge, release or APK operation occurred.
