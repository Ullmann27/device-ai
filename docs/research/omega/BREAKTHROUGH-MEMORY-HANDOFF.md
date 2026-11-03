# CONTINUUM Ω Intelligence Breakthrough — memory

Status: **WIP / UNVERIFIED**. Isolated research branch; no device or release claim.

- Branch: `codex/breakthrough-memory-20261103`.
- Agreed base: `64ec06a73f50cae9ee49035c840710d87777d50f` on Chief `codex/team-chief/omega-20261103`.
- Scope: EXP-12 persistence continuity and EXP-04 replay cost, using existing `FileLearningExperienceStore.replay` callers in `ContinuumLearningCore`.
- Prior memory handoff and brain inventory read. The current explicit master authorizes research, synthetic tests, push and draft PR; no owner data, production weights, signing, installation, main merge or parallel fact store.
- Runtime skill: managed environment observed ready, unrestricted network enforced; no secrets inspected. Quota: **NOT_VISIBLE**.

## Owned paths and hypothesis

`app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningExperience.kt` (replay and private derived-cache support only), new `app/src/test/java/at/heinz/deviceai/learning/LearningExperienceReplayCacheTest.kt`, this handoff and `docs/evidence/breakthrough-memory-20261103/`.

Proposed smallest slice: cache at most the most recent 128 replay-eligible records for one type. Reuse only after the existing content fingerprint and every LEX3 segment pass integrity validation under the current JVM/OS lease. Type changes, corrections, revocations, absence and corrupted files cannot reuse stale output. Caller receives fresh list ownership. Existing replay consumers are already connected; HybridMemoryRetriever/cognition/neural files are outside this lane.

Before implementation: freeze deterministic cold/warm/scale parity and negative invalidation tests, capture a fresh baseline with source SHA, allocation/RSS and latency evidence. Existing 4096/32 MiB bounds remain. Full decoding on cold/type-changing reads and full segment integrity I/O remain deliberate limits. No performance result claimed yet.

## Checkpoint

- Mandatory root instructions, experiment contract/register, issue #41, PR #287 and memory/inventory handoffs inspected.
- Tests: **NOT_RUN** for this branch.
- Implementation: **NOT_STARTED**.
- Rollback: remove the private replay optimization; no schema, files, migration or permissions change is planned.
- Next entry: tests and benchmark harness, then baseline run before any source edits.

Independent holdouts belong to `/root/quality`; this lane must not request unseen case contents. Reproduce future evidence using the prepared `/workspace/continuum-build-env/env.sh`; no heavy Gradle without Chief coordination.
