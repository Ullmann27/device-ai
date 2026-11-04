# G1-R6-LANDSCAPE-832-REPAIR-20261104

Status: SOURCE_INTEGRATED / focused host geometry PASS; Android suite and configured lint NOT_RUN. C1 #519 remains sole common integrator.

## Source and scope

Own draft #524, branch fix/continuum-r6-landscape-48dp-20261104, based on C1 9e84803cc1907d9618669ab039b1fd667ab5b207. Fix commit: 652e6e30e63a427546c6a30988906c351406be25.

The original layout blob 6acb79eaf1b90d49037acdfd4a7245e1119a271c is identical on failing #522 3704b62585c6ce6881ab0dfe0c9947cddd1531c2, requested C1 8557ef2813b54654ff3a70d979e7ee8e8eefedd8 and intake 9e84803. Comparison shows no changes to the six geometry sources between those C1 anchors; #522's production changes are memory/compiler only. The same pinned geometry implementation was executed, not three independent full Android builds.

Only ContinuumLandscapeCockpitLayout.kt changes in production. ContinuumHomeLayout, original tests, Activity, memory, assets, model files, signatures and update channel remain unchanged.

## Root cause and repair

At 832x700/font1, Float addition rounds the nominal pill bottom downward. Subsequent subtraction yields 47.999996185302734 instead of at least48. The initial source probe reproduced three failed target assertions. The fix advances only an undersized pill bottom to Math.nextUp(bottom). It leaves every HERO bound bit-identical and does not weaken any assertion or alter Box.height.

A 270-box before/after comparison across the original five windows and two font scales changed exactly three bottom edges, only the three presentation pills at 832x700/font1. All ten avatar boxes are identical.

## Own executed verification

JDK21.0.12.1 / kotlinc1.9.0. Six complete hash-verified production geometry files were compiled. Only theme/rail constant declarations were supplied by a host-only fixture; no layout calculations were substituted. This is not an Android, GPU or physical-device test.

- GeometryProbe: original ten configurations, three failures, exit1; repaired ten configurations, zero failures, exit0.
- Three frozen precision checks: original two FAIL/one PASS, exit1; repaired three PASS, exit0. The latter runner was repeated with the same result.
- The new Android JUnit wrapper invokes these same check methods but was not run under Android/JUnit in this environment. Original ContinuumLandscapeCockpitLayoutTest remains unchanged.
- A transcription error in the first uploaded hero snapshots was corrected in 7a343d84 before the production repair. Corrected test helper blob5ca3700d084a3914fba23526ef9309b02cc3f0fc equals the locally tested file; production blob245c8c94abdadbe065138561d2c9a2901456b3c2 also matches locally executed bytes.

Android SDK/Gradle/JUnit dependencies are not established locally; direct checkout/download failed DNS. No C1 shared Gradle slot or remote QA job was started. Full relevant Android tests and configured lint remain mandatory before acceptance, not implied by host PASS.

## Reproduction and handoff

From the fixed checkout, compile the six UI files ContinuumHomeLayout, ContinuumLandscapeCockpitLayout, ContinuumReferenceLayout, JarvisWindowProfile, JarvisCockpitGrid and JarvisShellLayout with evidence/ConstantsOnly.kt, the test helper and evidence/PrecisionRunner.kt. See adjacent evidence README for exact command. For RED replace only the landscape source with its immutable baseline blob. For common acceptance C1 must run the original landscape test, the new precision test, then the complete configured Android suite and lint on one frozen combined tree, using the established serialized build locks.

Rollback: reject/revert only this branch's small repair and its own tests/evidence. Do not reset C1 or #522. No release, signing, installation, model execution or historical-data changes.
