# CONTINUUM Ω Intelligence Breakthrough — memory

Status: **HOST_VERIFIED / INDEPENDENT_QA_PENDING**. Isolated research branch; no device or release claim.

- Branch: `codex/breakthrough-memory-20261103`.
- Draft [PR #399](https://github.com/Ullmann27/device-ai/pull/399), targeting Chief.
- Agreed base: `64ec06a73f50cae9ee49035c840710d87777d50f` on Chief `codex/team-chief/omega-20261103`.
- Frozen implementation: [`a6007442768b24f5aa700c61dae7c46049c1911a`](https://github.com/Ullmann27/device-ai/commit/a6007442768b24f5aa700c61dae7c46049c1911a).
- Clean baseline: [`2ad4099bb676d0785625bb12cbc4cd47b785a0f5`](https://github.com/Ullmann27/device-ai/commit/2ad4099bb676d0785625bb12cbc4cd47b785a0f5); original source is byte-identical to agreed base. Tests were frozen before implementation at `0e4a27f7170e29746e7ad3777a3cf21f21a9a1cf`.
- Scope: EXP-12 persistence continuity and EXP-04 replay cost, using existing `FileLearningExperienceStore.replay` callers in `ContinuumLearningCore`.
- Prior memory handoff and brain inventory read. The current explicit master authorizes research, synthetic tests, push and draft PR; no owner data, production weights, signing, installation, main merge or parallel fact store.
- Runtime skill: managed environment observed ready, unrestricted network enforced; no secrets inspected. Quota: **NOT_VISIBLE**.

## Owned paths and hypothesis

`app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningExperience.kt` (replay and private derived-cache support only), new `app/src/test/java/at/heinz/deviceai/learning/LearningExperienceReplayCacheTest.kt`, this handoff and `docs/evidence/breakthrough-memory-20261103/`.

Implemented smallest slice: cache at most the most recent 128 replay-eligible records for one type, per store instance. Reuse only after the existing content fingerprint and every LEX3 segment pass integrity validation under the current JVM/OS lease. Type changes rebuild the selection; writes clear it; foreign writes change the fingerprint; missing/corrupt files fail closed. Caller receives fresh list ownership. Existing `ContinuumLearningCore` replay consumers at lines 431, 508 and 1038 are already connected; HybridMemoryRetriever/cognition/neural files are outside this lane.

The existing `snapshotIfChanged` now delegates to a private already-locked helper, also used by replay, avoiding nested OS leases. Fingerprint lookup and selection replacement share one transaction. Cached records are immutable values and the retained list is a bounded copy. No cache is serialized; schema, export, deletion and capacity remain unchanged.

## Frozen verification and measurements

JDK17, official Kotlin2.4.0/JUnit4.13.2, synthetic temporary histories only. The baseline and candidate each passed **50/50 focused tests**, comprising 40 existing tests and 10 new replay regressions. New cases cover 4096-record cold/warm/order/limit/type parity, same-length/mtime LEX2 status changes, foreign-writer revocation, local no-op, owner correction/reconfirmation, empty-type invalidation, unrelated corrupt segments on negative-cache reads, missing files, deletion/recreation, defensive list ownership and concurrent readers/writers. All benchmark calls verify complete result equality.

Candidate also passed **6/6 existing feedback/multiprocess tests** and **4/4 real Linux syscall fsync(EIO) retry probes**. Each retry executes two successful directory fsyncs after the injected failure. No physical power-loss or device result is inferred.

Eight warmups followed by 25 measured samples per workload. `cold_instance` means a fresh Java store object with decoded-cache miss; the filesystem page cache is not flushed. Allocation is per-thread JVM allocation including the parity check; RSS is per-process `wait4` high-water accounting, not Android heap. Type-switch measurement is a pair of replay calls.

| Records / workload | Baseline p50 / p95 ms | Candidate p50 / p95 ms | Baseline → candidate allocation bytes |
| --- | --- | --- | --- |
| 256 warm same type | 5.747 / 7.274 | 0.443 / 0.551 | 6,928,728 → 415,712 |
| 256 cold instance | 10.696 / 15.887 | 8.748 / 27.157 | 7,068,224 → 7,105,456 |
| 1024 warm same type | 17.141 / 20.273 | 2.093 / 5.827 | 28,722,760 → 2,549,984 |
| 1024 cold instance | 18.912 / 24.241 | 21.401 / 26.696 | 28,727,336 → 28,729,384 |
| 4096 warm same type | 66.181 / 74.827 | 5.348 / 5.980 | 114,648,944 → 9,960,648 |
| 4096 cold instance | 60.926 / 68.450 | 58.367 / 63.536 | 114,650,744 → 114,654,752 |
| 4096 type-switch pair | 121.460 / 159.762 | 120.386 / 135.295 | 229,302,648 → 229,312,360 |
| 4096 warm absent type | 64.221 / 82.189 | 4.682 / 5.465 | 114,582,728 → 9,933,328 |

Complete benchmark process peak RSS: **202,176 → 187,952 KiB**; focused suite peak **372,892 → 373,924 KiB**. Compiler RSS is separately recorded. These single-host observations support a warm replay allocation/latency reduction, not a general RAM reduction or cold/type-switch speedup. The 4096 fixture uses 2,009,400 disk bytes; full byte-capacity roundtrip remains covered by the existing 33 MiB transfer test.

Evidence in `docs/evidence/breakthrough-memory-20261103/`: `baseline-2ad4099b/`, `candidate-a6007442/`, `durability-a6007442/`, `feedback-a6007442/`. Receipts include exact source hashes, SHA, clean tracked status, actual commands, exit codes and resource metrics. Failed initial measurement is retained under `baseline-harness-unavailable/`: compilation passed but `/usr/bin/time` was unavailable, so tests did not start. The corrected harness uses `os.wait4`. One candidate push raced the prior lane-owned checkpoint push and was rejected by Git's ref lock; ordinary sequential retry succeeded without force push.

## Reproduce and practical boundaries

```sh
source /workspace/continuum-build-env/env.sh
python3 docs/evidence/breakthrough-memory-20261103/run-focused.py \
  --deps /workspace/continuum-build-env/jvm-deps \
  --output /tmp/breakthrough-memory-fresh --benchmark
python3 docs/evidence/omega-memory-20261103/verify-durability-retry.py \
  --deps /workspace/continuum-build-env/jvm-deps \
  --output /tmp/breakthrough-memory-durability-fresh
bash scripts/continuum-feedback-review-jvm.sh /tmp/breakthrough-memory-feedback-fresh
```

Existing **4096-record/32 MiB** bounds remain. Full decoding on cold/type-changing reads, full segment integrity I/O on every warm read, and full materialization by `all()` remain deliberate limits. This is not a 10k-record index, general retrieval-scale improvement, model-quality gain or measured overall replay/training speedup. Cache storage is bounded per instance, not globally across arbitrarily many store objects. It is transient derived owner data, with no disk copy or separate consent state. Current app-private directories and protocol-aware writers remain assumptions; checksums cannot authenticate valid-file rollback.

Actual caller qualification: `teachIntent` confirms a lesson before replay, source training appends observations then switches SOURCE_TEXT to INTENT_LABEL, and curriculum/import checks normally append an audit afterward. Those writes/type switches deliberately miss the cache. Repeated same-type reads without intervening mutation (including interrupted curriculum retry sequences without an audit) can reuse it. The main dialogue uses `store.all()` and receives no improvement from this change. No end-to-end app benchmark or production cache-hit rate was measured. Cold p95 at 256 and cold p50/p95 at 1024 increased in this run; cold allocations are nearly unchanged, so no cold-path improvement is asserted.

`git diff --check` on production/test changes passes. The repository-wide check reports one trailing blank line in the preserved raw JUnit output `feedback-a6007442/junit.txt`; the raw log is retained byte-for-byte rather than rewritten.

Mandatory root instructions, experiment contract/register, issue #41, PR #287 and memory/inventory handoffs were inspected. Rollback is a source revert of `a6007442`; no data migration or recovery is required. Android/Fold7 latency, loaded-model RAM, thermal cost and physical power loss: **NOT_RUN**. Broad Gradle belongs to Chief/Quality.

Next entry: independent QA against frozen source, then Chief may cherry-pick the scoped commits after checking combined-source results. No automatic merge, release, signing or installation.

Independent holdouts belong to `/root/quality`; this lane must not request unseen case contents. Reproduce future evidence using the prepared `/workspace/continuum-build-env/env.sh`; no heavy Gradle without Chief coordination.
