# Code51 Android UI QA recovery – 2026-11-09

## Scope, exact baseline, classification
- Base: `copilot/code51-master-integrator-20261108` @ `60549c74cfaf437472ab2b02b86066c3ec23cba9`.
- Isolated recovery branch: `chatgpt/code51-ui-acceptance-recovery-20261109`; draft PR #665 targeting #663, **not** `main`.
- Source-bound failed GitHub Actions run: [42561770364](https://github.com/Ullmann27/device-ai/actions/runs/42561770364), JUnit artifact `code51-github-android-evidence` ID `13650139048`.
- CI: **4197 executed/discovered, 4164 passed, 15 failed, 18 skipped**. The `node-backend` job PASS, the `android-host` job FAIL at `:app:testDebugUnitTest`. No owner-package APK, original signing, Android/Fold7 release or device success.
- Input source had already fixed the previous invalid team request UUID in commit `60549c74`; do not redo that repair.

## Actual XML failures, grouped by cause
1. **Five `ContinuumReferenceCockpitUiTest` destination failures:** Owner-facing UI now opens `Mein KI-Team` through both `agents` and `agents-panel`, not historical `Team · Bots`. Exact destination comparison updated to the current approved label in all paths. The tests still tap real controls and verify the resulting dialog, accessibility/layout and no unexpected task.
2. **Five appearance-selection failures:** The existing dialog's neutral action is now `Film-Effekte`, not historical `Bewegung prüfen`. Update the exact label expectation only; retain all appearance choices and functionality assertions.
3. **Four `ContinuumVisualEvidenceUiTest` failures:** New cinematic state `HollywoodFuiConstruct` deliberately withholds actual panel data until its DATA phase (from 640 ms). After 80 ms the construction has an intermediate scale but `panel.alpha=0`; asserting positive content alpha before reveal contradicts the authored staging. Replace with an **exact zero-alpha assertion at 80 ms**, retaining the existing later strict `panel.alpha=1` and native content/geometry checks after ~960 ms.
4. **One `ContinuumSystemStatusWorkWindowUiTest` failure:** `Closing restores the projection` saw `0.9999056` vs exact `1.0`. `AvatarCameraMotion.advance` intentionally clamps simulated clock jumps to 100 ms per drawn frame (`DURATION_MS=520`). The old test advanced 1400 ms but drew only once. Simulate 14 x 100-ms advances **with a draw each step**, preserving exact camera reset assertion; no change to production easing/thermal behavior.

## Source changes
Only these three UI test sources differ from the frozen baseline:
- `app/src/uiTest/java/at/heinz/deviceai/ContinuumReferenceCockpitUiTest.kt`
- `app/src/uiTest/java/at/heinz/deviceai/ContinuumVisualEvidenceUiTest.kt`
- `app/src/uiTest/java/at/heinz/deviceai/ContinuumSystemStatusWorkWindowUiTest.kt`

**No** production source changes, no deleted tests, no changed skip policy, no weaker tolerance, no owner state changes. Do not confuse the new expected cinematic data phase with missing late content: after-settle assertions must remain.

## Acceptance and next executor
- **Evidence classification now:** `SOURCE_PATCHED / XML_ROOT_CAUSES_IDENTIFIED`; expected native UI repair **NOT_RUN** on this branch.
- Use a provisioned JDK21/Android SDK/Gradle host with genuine `-PcontinuumUiWidgetTests=true`; run `./gradlew :app:testDebugUnitTest :app:lintDebug` plus dedicated UI variant on the **exact candidate SHA**.
- Report exact new JUnit pass/fail/skip identities and image staging; if any failure remains, treat as RED and fix the actual cause, not by disabling/dropping assertions.
- Independently review and selectively integrate #665 into #663, then freeze a **new** Code51 source SHA and execute full `Continuum Code51 — source QA and unsigned APK` for that exact source.
- Only after full QA, compare real Code50 package/certificate, obtain the exact-source protected owner signing approval, then release an actually updatable `at.heinz.deviceai.continuumlocal` versionCode51 APK. Fold7 native/GPU/speech/thermal test is still separate and MUST NOT be marked PASS from Robolectric.

## Rollback
Revert only this branch's test expectation and frame-pump commits; never reset/force-push SOL/Astra branches or touch persistent owner/bot data, secrets, original signer or main.
