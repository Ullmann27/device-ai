# Memory R13 — relative-cue consistency and reproducible evidence

Status: **HOST_VERIFIED / C1_INTAKE_PENDING / ANDROID_NOT_RUN**. Draft #553, branch `fix/continuum-memory-r13-relative-cues-20261105`, stacked on R12 #548. One sequential specialist, no subagents, no shared-host build. This replaces the initial WIP plan with the executed result and continuation instructions.

## Exact source and ownership

- R12 base: `e8102654a34d435eee115643b414158cd34c6f1d`; full production blob `dcaeed0b13eb3f4f88128e45c250d6d84a319c72`.
- Fourteen regression methods published BEFORE production edits: `fce2c2a41ed7b8979b345ac72e07ca4b9087907d`; test blob `89332fdb45bf3c46050a3ed751bb16441c7f3244`.
- Tested production fix: `7f6a57c0b870af82a5a06d12d270b61e80901770`; production blob `d7639be8691d939f9aaa89c52bb85635ebe60f53`; remote tree `c6b7634ff7212e7f066a838852958111d9b8fd4f`.
- C1 #519 was read at `13c37f55789bf67b07beeb37cc1a5035526e623e`, including central board and discussion; #522, R12 handoff and review also read. #522 privacy/mapper and #524 layout are already bounded C1 integrations. R6–R12 retrieval prerequisites are NOT thereby accepted.
- Claim to C1: issue comment `6652567060`. No competing named R13 PR was observed; this is not proof of process-wide exclusivity or an external ACK.

Public repository/account checked. Only the existing `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt` is modified in production. BM25/Ngram scoring, snapshots, atomic publication and confirmed metadata are untouched by the R13 diff. C1 alone integrates shared Activity/factory/voice/worker entrypoints. No model weights, UI, owner records, original package/signature/update channel or offline controls changed. No main merge, Actions, paid build, signing, release, installation or APK. EXP-06/07/12 remain at their existing overall status.

## Two demonstrated repairs

1. The R12 numeric gate exempted only `vor N Tagen`. Other relative durations such as `vor 2 Wochen`, `seit 2 Tagen`, `in 3 Tagen`, `nach 2 Stunden` and singular `vor 1 Tag` wrongly required those numerals in document text and returned no memories. A shared bounded phrase recognizer now removes relative-duration numerals only from required numeric evidence. Other factual numbers, e.g. `4 Schrauben`, remain mandatory in both retrievers.
2. The previous day parser used literal spaces and could also match inside `bevor`, `zuvor` or `Tagenbuch`. The existing past-day window now uses the shared recognizer with word boundaries, ordinary/Unicode whitespace and singular forms. Existing date-by-date DST boundaries and overflow abstention stay intact. Actual matching-day ordering was tested in both retrievers, not just helper strings.

**Not implemented:** exact new week/month/year/hour/minute/future windows. Those durations avoid false factual-number exclusion but retain the existing lexical fallback when no existing temporal window is supported. Global leading-zero normalization was deliberately not applied: `Code 007` and `Code 7` remain distinct. Context-aware date equivalence (`05.11.` versus `5.11.`), spelled factual counts and more general temporal semantics remain open. The R12 review's leading-zero concern is therefore not marked resolved.

## Frozen tests and actual results

All source tests are under `app/src/test/java/at/heinz/deviceai/learning/`:

| Class | Methods | Git blob |
| --- | ---: | --- |
| MemoryRelativeCueR13Test | 14 new, frozen before repair | 89332fdb45bf3c46050a3ed751bb16441c7f3244 |
| MemoryNumericEvidenceR12Test | 9 unchanged | c6fc1b7ed6a8cbbbaf87c46a2e3e685a5878ed8e |
| TemporalCueCalendarTest | 15 unchanged | 17baa4a929361617da337615c4070149fce486b9 |
| TemporalCueDailyBoundaryTest | 12 unchanged | 63ada554c984cd42d235191d8df280bea86f02f2 |
| ContinuumRetrievalExperimentTest | 2 unchanged | e86554e48efe833e63d80783f46d3c2d6d36dfe5 |

The focused 23-case R13+R12 baseline: **14 PASS / 9 FAIL**. Intermediate duration-only correction: **18 PASS / 5 FAIL** (local source blob `5543f5c5ccff9d58f950249781fc7f6d3974d54d`, archived patch, NOT a separately published commit). Final shared day-parser correction: **23/23 PASS**.

The complete selected 52-method host suite: baseline **43 PASS / 9 FAIL**, runtime exit1; candidate **52 PASS / 0 FAIL**, runtime exit0. Compilation exit0 for both. All 38 existing methods remain green. No skips. These groups and repeated executions overlap; do not add them. This is the complete selected memory/temporal suite, NOT the complete Android app suite.

## Existing 1,000-document / 44-question experiment

The original corpus, deterministic distractors, queries and assertions were executed unchanged on both sources. This is a known repository regression set, not a fresh independent hidden holdout.

| Variant | Recall@1 count | Recall@3 count | MRR | Approximate index bytes |
| --- | ---: | ---: | ---: | ---: |
| BM25 | 34/44 | 36/44 | 0.7916666666666667 | 119644 |
| BM25 + time | 35/44 | 37/44 | 0.8181818181818182 | 119644 |
| Ngram | 37/44 | 40/44 | 0.8712121212121212 | 625732 |
| Ngram + time / default | 39/44 | 42/44 | 0.9212121212121214 | 625732 |

All numbers are identical before/after R13. Reflection readout uses the unchanged experiment's actual corpus/queries and real retrievers: **220 query/variant result lines** (44 times five, including overlapping default) preserve top-ten document IDs, confirmation flags and exact hexadecimal Double scores. Identical full readout SHA256: `f1d0f9c7a1714b329fd6c50e56b2805f95d65c2caa98aed373fec3283a06c6c6`.

Default quality is NOT perfect: `Wo habe ich geparkt?` misses t04 in top ten; `Wohin ging ich letzten Monat mit Thomas?` ranks t14 fifth. Passing the existing >=0.80 assertion does not resolve those misses or authorize promotion. No latency or device-speedup claim is made.

## Exact reproduction and tooling boundary

Actual complete final command, run from the reconstructed exact-source root `/mnt/data/continuum-memory-r13`:

```bash
python3 tools/memory-r13/run.py --baseline original/ContinuumMemoryRetriever.kt --output build/r13-final-replay
```

The supplied baseline file has blob `dcaeed0b13eb3f4f88128e45c250d6d84a319c72` and is also retained in the raw archive. Final driver blob: `99199b8a2564b11adbd1d63a1bf1540c5e795940`. Full command **exit0**: baseline compile0/test1, candidate compile0/test0, both readout0; expected counts checked, no timeout, source SHA256 unchanged before/after. The full driver was executed, not merely its individual subcommands.

In a full checkout containing the R12 ancestor, `python3 tools/memory-r13/run.py` obtains the same baseline using `git show e8102654a34d435eee115643b414158cd34c6f1d:app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`. That Git-acquisition variant was not executed here; local git HTTPS failed DNS/exit128. Do not label this a GitHub outage. The connector provided source bytes, which were reconstructed and checked against full Git blobs. No local/remote whole-repository tree equality is claimed.

The published driver is Linux/Python3 host tooling, using real Kotlin/JVM **1.9.0**, OpenJDK **21.0.12.1**, 512-MiB compiler/runtime heaps and bounded subprocesses. `JUnitHostR13.kt` provides explicit host assertions/annotation, NOT a JUnit4 engine. `EventEmittingBoundary.kt` fails immediately if constructed; default factory was exercised with null appDirectory, never the event path. No Android SDK/Gradle was present. Android compilation, project JUnit4, full app tests, NewApi lint, Activity/event delivery, native model/GPU/audio and Fold7 are **NOT_RUN**.

Both existing shared locks are respected if present; a partially available pair refuses execution. Neither shared path existed in this isolated host, which used its own flock. No shared Gradle job or other team's process was started/stopped.

A prepublication runner audit found that an already-existing output directory could have its receipt overwritten in the original driver. An own synthetic sentinel reproduced it (exit1, sentinel not preserved). Final driver assigns the writable output only after exclusive directory creation; same probe exit1 now preserves the sentinel. The final whole 52-test command was rerun after this tooling correction. No foreign data was used. This is a harness repair, not a third Android product feature.

## Raw evidence

See `../../evidence/memory-r13-20261105/RESULT.md` and `state.json`.

- `raw-evidence.tar.xz`: 17,312 bytes, 35 members, SHA256 `028f7c95bc9e5d368d6b8e775775a9ec7be31b2271dab99a8c38ae326f457874`. Initial baseline/intermediate/final logs, patches, original source, first complete driver receipt and both exact readouts. Its driver is the earlier v1; do not relabel its receipt as v2.
- `final-replay.tar.xz`: 5,640 bytes, 13 members, SHA256 `3d3cebf83e8550e741d16e5296ec1440092625212018251962a1d43cbbcc5489`. Runner guard RED/GREEN, v1-to-v2 diff, actual final complete command/exit, compile/test logs, versions and final receipt. Identical readouts are hash-referenced to the first archive in `shared-readouts.json`.
- Both archives include a `MANIFEST.json` for every other member. All members were reopened and their sizes/hashes verified locally. No independent reviewer or C1 runtime ACK is invented.

## Next entrypoint and rollback

C1: read this handoff and R12 review, then inspect the exact two production hunks in commit `7f6a57c0b870af82a5a06d12d270b61e80901770` and frozen `MemoryRelativeCueR13Test`. Resolve R6–R12 prerequisite intake first; do not replace a newer entire C1 production file with this older stacked file. Replay the pinned host command as evidence, then run the actual project compiler/JUnit4 for these five classes and the mandatory complete Android/unit/UI/lint gates on the eventual common source under C1's locks. New source does not inherit this host PASS. Leave contextual leading-zero handling and the two known retrieval misses explicit. APK remains after common acceptance and original-signing authorization.

Rollback only the R13 production delta and its new tests/tools as necessary, preserving prior fixes and negative evidence. No reset of shared branches. At this checkpoint all local test processes have completed; no helper/build is left running.
