# Operation Continuum Ω — Team 02 memory

Status: WIP / UNVERIFIED. Base #390: b4f61ae9765e26dd401db928b76fa9f233c0742a.
Branch: codex/team-02-memory-omega-20261103. EXP-12 continuity; EXP-04 replay boundaries unchanged.

The current master authorizes isolated implementation/tests/push/draft PR and supersedes historical v31 research freeze. No production memory, keys, main, release version, device install or model training is changed.

## Observed problem and design under test

`ContinuumLearningCore` constructs `FileLearningExperienceStore` directly. Its LEX2 ledger retains at most 256 records; protected owner records and revocation tombstones cannot be evicted, so the 257th fails. The M2 research proposal already identifies this issue. Consumers call `all()` and content-fingerprint snapshots; replay is capped at 128. `PersonalLearningTransfer` shares the legacy count constant and bounded portable format.

Investigate a bounded segmented LEX3 successor behind the existing store contract. Preserve ordinary LEX2 operation until protected capacity requires migration. Store a versioned manifest at the existing primary path so an old build rejects it rather than ignores a sidecar. Preserve exact original primary and backup bytes. Immutable checksummed segments plus a single atomic manifest replacement provide a commit point; missing/corrupt committed files fail closed. Never recover stale consent from an older manifest or backup. Bound record count, segment sizes and total read allocation; no new owner fact system.

## Validation plan

Start RED tests for >256 persistence across restart, 1000-day synthetic history, revocation, migration byte preservation, corruption, missing segments, downgrade refusal, interrupted writes, path safety and hard limits. Existing store and transfer regressions remain. New fixture data stays solely in temporary test directories.

NOT_RUN: new implementation, Kotlin/JVM, Android SDK, device, LLM quality. Quality agent owns shared harness setup.

## Rollback and next entry

Before activation the unchanged LEX2 source remains usable. After migration do not downgrade or restore LEX2 automatically: old bytes may predate revocation. Keep original files as diagnosis/owner-reviewed recovery evidence. Next: implement tests, reproduce capacity failure, implement bounded migration, rerun focused and relevant consumer tests. Raw evidence must record exact tested SHA.

## Checkpoint 2

Draft PR: https://github.com/Ullmann27/device-ai/pull/397. Initial docs and RED tests pushed as d9869c114a8bf7c51655e63d798151ac3f04253c. Three new capacity tests compiled and FAILED at the 256-record boundary; raw log in `docs/evidence/omega-memory-20261103/red-d9869c11.txt`.

Implementation WIP: LEX3 uses 32-record immutable LEX2-encoded segments (existing validated record codec), maximum 4096 records / 32 MiB decoded segment input, checksummed manifest at the original primary path, atomic-only replacement and fsync, cross-process file lock, original LEX2 primary preserved under `experiences-v3/legacy-v2.bin`, old `.lkg` never overwritten after migration. No automatic fallback. Ordinary legacy observation churn retains 256-bound behavior; protected overflow or legacy byte overflow migrates. CLPT schema unchanged; count/byte acceptance expands to 4096/32 MiB with same review-only semantics. Current users of `all()` still materialize all records: this is a bounded milestone, NOT tens-of-thousands scale or indexed retrieval.

First development run: 17/17 focused Kotlin/JVM tests PASS using official Kotlin 2.4.0 and JUnit 4.13.2 / JDK17 (shared quality harness); this was an uncommitted working tree, not final frozen evidence. Additional negative, process, holdout and capacity tests added; not yet run. Android/device/power-loss fsync behavior NOT_RUN. Independent review should check directory fsync portability, canonical-path trust assumption, commit/cleanup order, transfer allocation and readAll cost. Host checksums do not authenticate malicious filesystem rollback.

## Checkpoint 3 — independent failure retained and corrected

Frozen source 28c77782: expanded focused suite ran 27 tests with 26 PASS / 1 FAIL. Both this lane and Quality independently identified an incorrect holdout oracle: repeated `updateStatus` with an unchanged status deliberately leaves `updatedAt` unchanged. Only that expectation changed; production status semantics remain. Full failed synthetic-only log retained. The `/usr/bin/time` executable was unavailable; isolated Python `resource.getrusage(RUSAGE_CHILDREN)` recorded peak child RSS 356756 KiB for this failed suite (256 MiB JVM heap; RSS includes JVM/native overhead). This is not device RAM evidence.

Additional work: bounded transfer output allocation, 4096 input batch limit, large Unicode/byte-capacity controls, real child-JVM halt before manifest commit, post-rename failure idempotence, corruption/version negatives, and existing temporal benchmark capacity expectation updated from256 to300 (Chief lease approved). The source still needs a fresh frozen run. A directory fsync error after atomic replacement can report an uncertain outcome: the new complete ledger may already be committed. Callers must retry idempotently, never restore stale consent. Directory FileChannel fsync portability remains an Android-device gate.

## Checkpoint 4 — frozen positive run and independent export boundary finding

Source04f9db6bd4dfa236983bb3ebcfb861c04977c8cf passed39/39 focused/historical JVM tests (10.501s JUnit, 11.003s process;256MiB heap, peak child RSS358972KiB). Synthetic1000 days at4 records/day retained4000 records: append41.47ms, seven full reads p5086.83ms/max135.94ms, disk829186 bytes. The full scan cost is material: scaling beyond4096 needs query/index API work, not merely increasing a constant. Existing feedback+real multiprocess suite also passed6/6 against these production bytes; its explicit source-list harness required the new Lex3 source dependency.

Independent reviewer reproduced a real export boundary bug on the same source:4096 valid records consuming33,553,407 LEX3 bytes fit the32MiB store, but CLPT metadata pushed export above its initial32MiB cap. Raw independent source/output preserved. Fix: portable cap33MiB; store cap stays32MiB. CLPT overhead relative to all segment bytes is `12*N - 40*S + 48 + packageUtf8Bytes`; package<=256 ASCII bytes and N<=4096 imply extra<=49,456 bytes, well within1MiB. New complete boundary roundtrip uses maximum256-character package and nearly full store bytes. No partial export or dropped records. Fresh source verification pending.

## Checkpoint 5 — real syscall fault review

Independent export probe passed on eabda7ffe0e28f10ba0d8909745c61fe674d3971: original4096-record near-capacity ledger exported33,597,519 bytes and all inspected fields matched.

A second independent finding used **real strace fsync(EIO) injection** at the parent-directory sync after atomic rename: the first status update threw, but an unchanged-status retry returned true without another fsync. Content idempotence alone did not settle uncertain durability. Store sources were compiled at04f9db6b and byte-identical at eabda7ff; exact hashes: ContinuumLearningExperience.kt `0a656170e3700fef344c807c38f50c68c125eb45e848f6c5bda97ea663cd5bbe`, Lex3LearningExperienceStore.kt `aaf74faec3b22ce623fa9fda8e7e0481db5a8aa81d0041b230f445bfa01ccc63`. Raw probe/trace preserved.

Fix under verification: every normally returning mutation on active LEX3 forces root+parent directories under the same OS lease, including no-op status, duplicate append, empty appendMissing and updateMatching. Original exceptions propagate; a retry may acknowledge success only after the new barrier succeeds. Read-only paths unchanged. Added a repeatable Linux strace regression for all four no-op paths with disposable synthetic ledgers; no production fault-injection API. This is syscall-failure evidence, not observed power-loss data loss or Android verification.
