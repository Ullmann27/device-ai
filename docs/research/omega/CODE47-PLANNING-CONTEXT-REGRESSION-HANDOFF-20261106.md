# Code47 Planning context regression

Status: RED_TO_GREEN; 6/6 PASS after the real Activity correction. Owner-authorized Review4497468613 fix.
Base test checkpoint ee0c2e28bcb4aae30bc94f6b44c81c0903cbf3b3 is preserved.
Production remains c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e on this test-only branch.

Scope: OwnerAppPlanningContextUiTest, EXP-05/EXP-12, real UI send → local inference
prompt → callback context recorder → transcript/restore. No production edits.
Minimal production deletion and RED were sent to Bot1, the only Activity writer.
Baseline: two intended failures, four controls pass. Identical bytecode GREEN: six pass. Full raw evidence and exact
commands: docs/evidence/code47-planning-context-regression-20261106.

Verified GREEN overlay source SHA-256:
`b3551528e76061191a29d2cbaf9be7ff9d183dea9d3757e3c22ab7091bd25e38`.
Test bytecode and all 69 overlay classes unchanged before/after the run.
Next: Root integrates this test-only continuation with Bot1's production deletion.
The five newly assigned context unit failures will be handled in a later checkpoint.
No second Gradle, extra features, main merge, release or signing actions.
Rollback: omit this test-only continuation; preceding ee0c2e28 remains unchanged.
