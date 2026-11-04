# G1-R6-LANDSCAPE-832-REPAIR-20261104

Status: CLAIMED / implementation NOT_RUN. Owner-authorized bounded repair; C1 #519 remains the only common integrator.

Base: #519 `9e84803cc1907d9618669ab039b1fd667ab5b207`. Requested comparison anchors: #522 `3704b62585c6ce6881ab0dfe0c9947cddd1531c2` and C1 `8557ef2813b54654ff3a70d979e7ee8e8eefedd8`.

Read-only diagnosis from #519 comment 6641438364: at 832 x 700 dp and fontScale 1, a nominal 48f pill becomes 47.999996185302734 through Float addition/subtraction. The memory specialist claims no UI edits. This report is an input, not our own test execution.

## Plan

- [ ] Verify exact layout/source identities across requested baselines and current C1.
- [ ] Reproduce the unchanged geometry failure with pinned Kotlin sources.
- [ ] Add a strict >=48f regression and preserve existing assertions and avatar bounds.
- [ ] Repair only the proven numerical boundary in ContinuumLandscapeCockpitLayout.kt.
- [ ] Repeat all available focused checks on identical sources; record Android/Gradle/Lint availability separately.
- [ ] Publish exact source hashes, commands, outputs and rollback; hand to C1 without merging.

Allowed production files: app/src/main/java/at/heinz/deviceai/ui/ContinuumLandscapeCockpitLayout.kt and, only if necessary, ContinuumHomeLayout.kt. Associated layout tests and own evidence only. No Activity, memory, avatar asset, signing, update channel or model changes.

This session has Kotlin/JDK locally but no established Android SDK/Gradle cache. Direct network checkout failed DNS. GitHub connector reading/writing works. No shared Gradle job or remote QA run has been started. Any source-only harness boundaries must be disclosed.

Rollback: do not integrate this branch unless review accepts the patch; preserve all C1/#522 sources and negative evidence. No main merge, signing, release, installation, paid tools, Actions or model training.
