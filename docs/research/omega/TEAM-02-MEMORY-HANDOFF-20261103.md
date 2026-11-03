# Operation Continuum Ω — Team 02 memory

Status: WIP / UNVERIFIED. Base #390: b4f61ae9765e26dd401db928b76fa9f233c0742a.
Branch: codex/team-02-memory/omega-20261103. EXP-12 continuity; EXP-04 replay boundaries unchanged.

The current master authorizes isolated implementation/tests/push/draft PR and supersedes historical v31 research freeze. No production memory, keys, main, release version, device install or model training is changed.

## Observed problem and design under test

`ContinuumLearningCore` constructs `FileLearningExperienceStore` directly. Its LEX2 ledger retains at most 256 records; protected owner records and revocation tombstones cannot be evicted, so the 257th fails. The M2 research proposal already identifies this issue. Consumers call `all()` and content-fingerprint snapshots; replay is capped at 128. `PersonalLearningTransfer` shares the legacy count constant and bounded portable format.

Investigate a bounded segmented LEX3 successor behind the existing store contract. Preserve ordinary LEX2 operation until protected capacity requires migration. Store a versioned manifest at the existing primary path so an old build rejects it rather than ignores a sidecar. Preserve exact original primary and backup bytes. Immutable checksummed segments plus a single atomic manifest replacement provide a commit point; missing/corrupt committed files fail closed. Never recover stale consent from an older manifest or backup. Bound record count, segment sizes and total read allocation; no new owner fact system.

## Validation plan

Start RED tests for >256 persistence across restart, 1000-day synthetic history, revocation, migration byte preservation, corruption, missing segments, downgrade refusal, interrupted writes, path safety and hard limits. Existing store and transfer regressions remain. New fixture data stays solely in temporary test directories.

NOT_RUN: new implementation, Kotlin/JVM, Android SDK, device, LLM quality. Quality agent owns shared harness setup.

## Rollback and next entry

Before activation the unchanged LEX2 source remains usable. After migration do not downgrade or restore LEX2 automatically: old bytes may predate revocation. Keep original files as diagnosis/owner-reviewed recovery evidence. Next: implement tests, reproduce capacity failure, implement bounded migration, rerun focused and relevant consumer tests. Raw evidence must record exact tested SHA.
