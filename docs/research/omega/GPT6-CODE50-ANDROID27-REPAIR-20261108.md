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
