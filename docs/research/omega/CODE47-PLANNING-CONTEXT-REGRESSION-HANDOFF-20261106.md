# Code47 Planning context regression

Status: RED_REPRODUCED; GREEN_PENDING. Owner-authorized Review4497468613 fix.
Base test checkpoint ee0c2e28bcb4aae30bc94f6b44c81c0903cbf3b3 is preserved.
Production remains c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e on this test-only branch.

Scope: OwnerAppPlanningContextUiTest, EXP-05/EXP-12, real UI send → local inference
prompt → callback context recorder → transcript/restore. No production edits.
Minimal production deletion and RED were sent to Bot1, the only Activity writer.
Six cases: two intended failures, four controls pass. Full raw evidence and exact
commands: docs/evidence/code47-planning-context-regression-20261106.

Next: run these unchanged compiled tests with Bot1's real patched Activity overlay,
record both source and binary hashes and GREEN result, then hand the commit to Root.
No second Gradle, extra features, main merge, release or signing actions.
Rollback: omit this test-only continuation; preceding ee0c2e28 remains unchanged.
