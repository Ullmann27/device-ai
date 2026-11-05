# R13 source-bound result — 5 November 2026

**HOST_VERIFIED; C1 integration and Android acceptance pending.** Draft #553. Production commit `7f6a57c0b870af82a5a06d12d270b61e80901770`, blob `d7639be8691d939f9aaa89c52bb85635ebe60f53`; baseline R12 `e8102654a34d435eee115643b414158cd34c6f1d`, blob `dcaeed0b13eb3f4f88128e45c250d6d84a319c72`. New 14-method test was frozen in `fce2c2a41ed7b8979b345ac72e07ca4b9087907d` before repair.

## Actual product changes

Only GermanText's numeric-evidence exemption and TemporalCue's existing past-day recognition change inside the existing ContinuumMemoryRetriever.kt. Relative-duration numbers no longer hard-exclude valid memories; unrelated factual counts stay required. Past-day recognition now agrees on singular forms, ordinary/Unicode whitespace and word boundaries. Existing index snapshots, publication, scores and confirmation metadata remain unchanged.

No new exact week/month/year/future/hour/minute time windows are claimed. Leading-zero identifiers remain distinct; date-specific equivalence is still open.

## Executed gates

| Gate | Baseline | Candidate |
| --- | --- | --- |
| New R13 14 + old R12 9 | 14 PASS / 9 FAIL | 23 PASS / 0 FAIL |
| Intermediate duration-only change | 18 PASS / 5 FAIL | Not final; patch retained |
| Five complete selected memory/temporal classes | 43 PASS / 9 FAIL | 52 PASS / 0 FAIL |
| Compiler | Exit0 | Exit0 |
| Expected test runtime | Exit1 | Exit0 |
| Original 1,000-doc / 44-query experiment | Default rank1 39/44, rank3 42/44 | Identical |
| Exact top-ten result/score readout, 44 queries x 5 variants | 220 result lines | 220 byte-identical result lines |

The groups and replays overlap. The 220 comparisons are not 220 additional unit tests. The known original experiment is not a new independent hidden holdout. Remaining default misses: parked-car t04 outside top ten; Thomas/theater t14 fifth. Original assertions unchanged; no new quality promotion or speedup claim.

## Complete final replay

```bash
python3 tools/memory-r13/run.py --baseline original/ContinuumMemoryRetriever.kt --output build/r13-final-replay
```

Executed from `/mnt/data/continuum-memory-r13`: driver exit0, all subprocesses match expected exits, no timeouts, exact source hashes unchanged. Final driver blob `99199b8a2564b11adbd1d63a1bf1540c5e795940`. Direct git-baseline acquisition remains unexecuted because no full checkout was available; explicit baseline acquisition was executed end-to-end.

Linux host, Kotlin1.9.0, OpenJDK21.0.12.1; full production file and original source tests with a visible host assertion adapter, not JUnit4/Android. Event decorator adapter throws on construction and was not entered. No event/Activity, Android/Gradle/lint, model, GPU, audio, physical-device or APK acceptance.

## Raw archives and integrity

| File | Bytes | Members | SHA256 |
| --- | ---: | ---: | --- |
| [raw-evidence.tar.xz](raw-evidence.tar.xz) | 17312 | 35 | 028f7c95bc9e5d368d6b8e775775a9ec7be31b2271dab99a8c38ae326f457874 |
| [final-replay.tar.xz](final-replay.tar.xz) | 5640 | 13 | 3d3cebf83e8550e741d16e5296ec1440092625212018251962a1d43cbbcc5489 |

All members were reopened and checked against each archive's MANIFEST.json (sizes and SHA256). The first archive's complete replay used driver v1. The supplement records the corrected v2 driver and full replay, including the synthetic existing-output receipt guard: before sentinel overwritten, after sentinel preserved; both correctly rejected an existing output directory with exit1. The guard fix was followed by a fresh successful full command.

Final readout SHA256 for BOTH sources: `f1d0f9c7a1714b329fd6c50e56b2805f95d65c2caa98aed373fec3283a06c6c6`. The supplement's shared-readouts.json references these exact first-archive members to avoid duplicate data. Receipts include actual argv, UTC start times, durations, exits and log/source hashes. Subsequent publication only adds tools/docs/evidence, not another production change.

[Full continuation handoff](../../research/omega/MEMORY-R13-HANDOFF-20261105.md) · [Machine state](state.json). C1 remains sole common integrator. No main merge, Actions, paid build, package/signature/offline or owner-data changes, release or install.
