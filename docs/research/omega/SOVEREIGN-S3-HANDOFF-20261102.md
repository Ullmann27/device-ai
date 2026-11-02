# Sovereign cognition S3: Observed cognitive task and review-only experience

Milestone commit: `0e22bdb75124e46dce14f4d46977bedcb235751c` · [Draft PR357](https://github.com/Ullmann27/device-ai/pull/357).
Follow-up code repair: `aabd8ea7dcd09863fb5fe214ed027cd145ce2fb0`; stronger Android source contract: `babba3326475521bfc8d03807ee1ad8873e01b91`.

SOURCE_INTEGRATED / HOST_VERIFIED within the stated focused scope; DEVICE_VERIFIED=NOT_RUN. 

Actual path: BootstrapBrain → ReadOnlyToolPlan → BoundedAgent → ToolGate → DeviceStorageProbe → evaluator → ExperienceCompiler; same CognitiveTurnTrace to Android caller.

Changed/reused files: AgentTaskEvidence.kt, BoundedAgent.kt, CognitiveTurnTrace.kt, ReadOnlyToolPlan.kt, AgentToolLayer.kt; task identity/evidence tests.

Tests and result: Identity collision RED→GREEN; cancellation phase2RED→GREEN; focused task suite7 PASS. Three actual host storage probes independently compared with JDK FileStore and persisted receipts; one candidate from3 distinct episodes, requires owner approval. Review timeout/cancel race controls included in12-case task/review suite.

Limits: Metadata is informational, never authority. Read-only policy≠explicit owner approval. Replayed output, form-only evaluator, timeout/denial/cancel do not confirm completion. Already-running code is not an OS sandbox; consumers order cross-thread events by task/attempt/sequence. CCC1 is unchanged; this is not a coding-success benchmark.

Complete raw evidence: `docs/evidence/sovereign-cognition-20261102/`; final acceptance commands/source digests/status: `final-verification.json`. Historical failures remain in the same directory. Read [central handoff](SOVEREIGN-COGNITION-HANDOFF-20261102.md), [architecture matrix](SOVEREIGN-COGNITION-ARCHITECTURE-20261102.md) and the research result before continuing. Native Gradle/LiteRT/Fold7/model tests NOT_RUN; no APK built/signed/installed, no main/merge, no owner-memory or productive weight change.

Reproduction: existing `scripts/android-kotlin-unit-check.sh` with the documented Kotlin2.4/JUnit/Android-all dependencies; `node scripts/continuum-reliability-qa.mjs critical --out <fresh-outside-checkout-dir>`; full Node with profile`full-node`. S2 additionally uses `scripts/continuum-context-budget-audit.sh <parent-main-classes> <candidate-main-classes> <fresh-output>` with the same compiler/stdlib/JUnit/json environment documented in that script.

Rollback: Revert scoped S3/review changes. Existing receipts/experiences stay in the same stores; no migration or productive-data change performed.
