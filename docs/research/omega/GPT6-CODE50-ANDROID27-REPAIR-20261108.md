# Code50 Android JUnit acceptance repair — 08.11.2026
Source: QA staging PR #630 at `1e5b3876a24e379af186e9f08f1255c1b0cf77d6`, containing merged Node 22 fix #631.

## Baseline
GitHub Actions run [42461426218](https://github.com/Ullmann27/device-ai/actions/runs/42461426218) at `c6e29e2f0bd108c141dea885bdd4b34d509c2356`. Android `:app:testDebugUnitTest`: **4022 tests, 27 FAIL, 20 SKIP**, Gradle build FAILED after 21m51s. `lintDebug` and APK assemble NOT_RUN due failed JUnit. Artifacts `code50-github-android-evidence` ID `13609838295` (JUnit XML). No signed/unsigned APK.

## Failure clusters
- 1 stale TTS UI assertion expecting 16 rather than Code50 32 pieces.
- 3 `EntityRoleResolutionEngineTest`: edge labels SISTER/CHILD mismatch German utterance nouns Schwester/Tochter; must map lexical labels while continuing to require supplied evidence edges.
- 1 LocalReasoningWorkScenePolicy: complex strategic comparison not classified as immediate.
- 1 AvatarMotionEvidenceTest: old window-gaze clamp expectation versus current kinematics, needs source review.
- 4 layout geometry/hit-area/floating-point precision tests.
- 3 JarvisLiveAvatar responsive UI expectations.
- 2 ContinuumVisibleAvatar UI parent expectations.
- 11 workscene/deck-return tests and document scene.
- 1 JarvisSequenceTurnLifecycleUiTest state-count mismatch.

## Invariants
Do not reduce 48dp targets, throw away deck interaction, misreport workscene events, invent kinship facts, or ignore stale callbacks. No main, publisher, signing material or original owner identity changes.

## Admission
Isolate verifiable functional patches; rerun targeted Android tests on GitHub or functioning local SDK before full unit/lint and original APK signing.

## Validated Android RED → GREEN stages (same code50 QA lineage)
- Initial [GitHub run 42461426218](https://github.com/Ullmann27/device-ai/actions/runs/42461426218): 4,022 Android host tests, 27 failed, 20 skipped (at SHA `c6e29e2f0bd108c141dea885bdd4b34d509c2356`). This was **red**.
- Focused [GitHub run 42464605953](https://github.com/Ullmann27/device-ai/actions/runs/42464605953): 27 original failing tests, **25 passed / 2 failed**, 0 skipped (at SHA `2fd9425c825a54214c81fee2bf3eafb2208761eb`). Fold/rotation NPE and 3D camera projection remained.
- Narrow [GitHub run 42466819328](https://github.com/Ullmann27/device-ai/actions/runs/42466819328): **1 pass / 1 fail** (at SHA `5ba743151d76dc828d39ac7d975b864173c46ad8`). Rotation/fold passed after immediate work-window retirement on configuration change. 3D camera diagnostic measured rest=1, current=1, motion=1, safe sampled target=.92: projection state was stale while the camera animation progressed.
- Final narrow [GitHub run 42467545703](https://github.com/Ullmann27/device-ai/actions/runs/42467545703): **2/2 passed, zero failures** at SHA `c2291cd799dec85fcccb44a1db387267ca85e411`. Verified actual JUnit XML (`code50-android27-targeted-evidence`, artifact ID 13611978942). Fix: cached shared 3D projection is updated on the existing avatar frame clock, not solely when a Canvas draw occurs; no second animation loop and no bypass of keep-out safety.
- The other 25 focused tests were green on an earlier commit, **not yet rerun alongside the final two fixes**. Full 4,022 JUnit + lint + unsigned Code50 APK assembly on the **merged exact SHA** remain **NOT_RUN**.

## Review cleanup
- Removed temporary repair-branch GitHub Actions trigger and targeted acceptance job. Staging inherits its existing workflow unchanged; no persistent CI workflow diff is introduced by this repair PR.
- Replaced tautological comparison of two calls to the same layout constructor by explicit, independent HERO gap contracts against the MODEL column, SYSTEM_STATUS column, avatar controls, and composer. Exact deterministic replay retained. 48dp target minima remain enforced.
- German kinship mapping remains edge-bound and gender-checked; masculine CHILD cannot satisfy `Tochter`. No invented family relation is admissible.

## Release boundary
Originalsignierte Code50 (0.3.49 / versionCode 50) **NOT_BUILT**. No build artifact, SHA256, Fold7 run or signature claim. Main and original publisher unchanged.
