# Sovereign cognition S1: Canonical complete-evidence model context

Milestone commit: `c1af43392af6efa208b36724771fb7c0892c5deb` · [Draft PR357](https://github.com/Ullmann27/device-ai/pull/357).
Follow-up code repair: `aabd8ea7dcd09863fb5fe214ed027cd145ce2fb0`; stronger Android source contract: `babba3326475521bfc8d03807ee1ad8873e01b91`.

SOURCE_INTEGRATED / HOST_VERIFIED within the stated focused scope; DEVICE_VERIFIED=NOT_RUN. 

Actual path: AgentActivity.runLocal → AiContextCompiler → optional ChatReasoningRouter/final prompt → existing LocalModelRuntime.

Changed/reused files: AiContextCompiler.kt, AgentActivity.kt, ChatReasoningRouter.kt, LocalChatPromptBudget.kt; corresponding context/privacy/router/budget tests.

Tests and result: Initial 6 cases:5 real failures; first fix8 PASS. Review adds ungrounded-advisory/task/cancellation/redaction regressions; focused context/privacy/router suite30 PASS. Final whole-source acceptance is separately pinned in final-verification.json.

Limits: No memory write or model promotion. Whole selected memory/research groups may be omitted under tight budgets; mandatory context fails closed. Request longer than7900 is clarified, never shortened.

Complete raw evidence: `docs/evidence/sovereign-cognition-20261102/`; final acceptance commands/source digests/status: `final-verification.json`. Historical failures remain in the same directory. Read [central handoff](SOVEREIGN-COGNITION-HANDOFF-20261102.md), [architecture matrix](SOVEREIGN-COGNITION-ARCHITECTURE-20261102.md) and the research result before continuing. Native Gradle/LiteRT/Fold7/model tests NOT_RUN; no APK built/signed/installed, no main/merge, no owner-memory or productive weight change.

Reproduction: existing `scripts/android-kotlin-unit-check.sh` with the documented Kotlin2.4/JUnit/Android-all dependencies; `node scripts/continuum-reliability-qa.mjs critical --out <fresh-outside-checkout-dir>`; full Node with profile`full-node`. S2 additionally uses `scripts/continuum-context-budget-audit.sh <parent-main-classes> <candidate-main-classes> <fresh-output>` with the same compiler/stdlib/JUnit/json environment documented in that script.

Rollback: Revert this branch S1 and review-fix changes as a reviewed unit; do not reset shared branches.
