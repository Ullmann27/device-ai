# Code47 routing regression handoff

Status: WIP / UNVERIFIED. Base c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e; isolated branch codex/continuum47-routing-regressions-20261106, targeting codex/continuum-code47-qa-candidate-20261106.

Scope: correct the incomplete ContinuumBotTaskRoutingUiTest fixture and update two existing source-contract tests for the integrated workscene guard and bounded storage branch. No product changes planned, no ToolRegistry/NativeAppLookup ownership, no second Gradle process.

Cause: the intake-only UI fixture skips onCreate and leaves localRuntime uninitialized; the normal Activity initializes the real LocalModelHost runtime before buildUi. The new truthful identity call reads that runtime. The updated fixture must retain proofs of no loaded model, no generation, no orchestrator and no bot mutation. The Node contracts must retain all cockpit guards and isolate STORAGE from the following AppLookup planning path.

Tests: existing combined Android run reports UninitializedPropertyAccessException in unknownNaturalSelectionsReachTheOrdinaryInputPath; detailed XML pending. Targeted Node baseline recorded locally before edits. Updated Android UI verification remains NOT_RUN here and is owned by the combined root SDK run.

Compatibility: EXP-05 and EXP-12 unchanged; no runtime, identity, persistence, model, gate, renderer or wire-format changes. Rollback: revert this isolated test-only change. Next: targeted Node RED/GREEN plus preserved Android fixture invariants, then root integration and actual UI rerun.
