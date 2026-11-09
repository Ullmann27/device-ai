# Continuum Cinematic — Work-Alive Raster, 09.11.2026

## Exact source and ownership
- Source branch: `chatgpt/cinematic-sustained-hologram-20261109`.
- Tested standalone policy + committed app integration: [`64535f994f8e1e420dd5187f928d361e1525f331`](https://github.com/Ullmann27/device-ai/commit/64535f994f8e1e420dd5187f928d361e1525f331).
- Parent: Astra Holographic V2 PR #659 at `69e9b3986d9fad86bda64b3e47c43073e1c76d3a`.
- This is an independent, optional overlay for master integrator PR #663. **No mutation of #659, #663, #664, #665, main, APK, signing or release refs.**
- No GLB, morph targets, camera control, TextToSpeech engine, owner settings, tools or memory modified.

## Finding / low-risk fix
Before: `HollywoodFuiStageOverlay` sent `frame.grid * (1f - frame.data)` to `HollywoodFuiCinematics.drawGridAndScan`. At the end of the 900-ms construction `frame.data = 1`, thus inner raster and scan are exactly **0** even while a real task is actively working. The already existing energy rim and motes remain, but the window's interior is static.

After: `HollywoodFuiWorkActivity.rasterOpacity(grid, data, active, animate)` preserves the original opening raster and leaves exactly **0.09** of its original brightness when `active && budget.animate && !reducedMotion`. The actual raster and scan are even dimmer because their existing painter alpha coefficients are retained (grid 0.16 * 0.09 = 0.0144; line 0.75 * 0.09 = 0.0675 maximum). There is no synthetic progress or new status value.

The ongoing scan keeps using `HollywoodFuiConstruct.frame(elapsedMs)` and its existing 2200-ms scan period. A deterministic 8%-period edge envelope fades the moving line at the top and bottom of its sweep, preventing a visually abrupt wrap. No new thread, timer, invalidator, view, event source, GPU pass or asset.

`setWorkActive(false)` and no-animation/reduced-motion/low-budget modes make settled raster opacity zero. Existing protected avatar/orb/input bounds and native text z-order remain unchanged. Closing still ends active frames, STOP state stays authoritative. The scan is decorative, not evidence that work is progressing.

## Files
- `app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiWorkActivity.kt`: pure bounded opacity and wrap envelope.
- `app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiStageOverlay.kt`: attach to the existing real-work boolean and common render budget.
- `app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiCinematics.kt`: fade the scan line at its wrap.
- `app/src/test/java/at/heinz/deviceai/ui/HollywoodFuiWorkActivityTest.kt`: 4 JUnit contract tests, committed; JVM/Gradle test execution not claimed.

## Executed verification
A standalone copy of the exact new pure policy was compiled under JDK 21 / kotlinc 1.9.0 and run with **1015 passing deterministic checks**, including 1001 interpolation samples, invalid input, low-power/idle states, and scan edge. No crashes/failures.
Sampled existing constructor equations at 900, 3000, 5000, 12000 ms: previous settled raster = 0, new working settled raster = 0.09, new idle settled raster = 0. A CSV trace and comparison graph were produced locally for handoff (not native app screenshots).

**Not executed here:** complete repo Kotlin/Android UI tests, native Filament/3D rendering, actual Fold7 pixels, frame rate, native audio or memory performance. The exact repo sources could only be edited through the GitHub connector; Google Maven/Android SDK were unavailable in the isolated local check.

## Reproduce/accept
1. On the authorised provisioned source host, checkout exact commit `64535f994f8e1e420dd5187f928d361e1525f331` (or cherry-pick onto the approved Code51 integration tree, never push directly to #663).
2. Run `scripts/android-kotlin-unit-check.sh <output-dir>` and targeted `./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.ui.HollywoodFuiWorkActivityTest :app:lintDebug` if AGP/SDK are available.
3. From a real active work window, verify settled overlay after 900 ms shows a faint moving scan; after STOP/idle it vanishes. Compare Fold/phone; dark glass must keep the native text legible; reduced motion/power save/thermal controls stop it.
4. Integrate only after independent review and source-exact QA. Code51 owner-release freeze always wins. Otherwise retain as a Code52 graphics refinement.

## Rollback
Revert only source commit `64535f994f8e1e420dd5187f928d361e1525f331`. The existing work window, native text, energy rim, eye tracking, original avatar and voice continue unchanged. No migration or owner-data rollback is necessary.

## Milestone 2 — sentence-boundary gestures, source `2a41a826fea68012a869c92c3b5d39b81634000c`

The Android TTS chunk correction in #664 and this isolated change solve different layers. `JarvisVoiceProfile` is unchanged; `AvatarDirector.sentenceEndAfter` no longer emits a premature `SentenceEnd` expression cue for a recognized title followed by a capitalized personal name (`Dr. Müller`, `Prof. Huber`, `Mag. Leitner`, `Ing. Weber`, `DI. ...`, and selected English equivalents), nor for `Art. 5`, `Abs. 3`, `Nr. 4` before numbers. The pronunciation, punctuation and existing TTS callback ordering are untouched. Actual ends such as `... antwortet.` remain sentence boundaries.

Files:
- `app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt`: one pure policy admission after existing punctuation and word-boundary checks.
- `app/src/main/java/at/heinz/deviceai/avatar/AvatarSentenceAccentPolicy.kt`: bounded, clockless rule with no transcript retention.
- `app/src/test/java/at/heinz/deviceai/avatar/AvatarSentenceAccentPolicyTest.kt`: four additional JUnit checks, including a Director→SentenceEnd contract.

A separately compiled exact policy copy, JDK 21 / kotlinc 1.9.0, passed **2036 deterministic standalone checks** across common titles, legal references, real sentence ends and malformed indices. Full JUnit, Android TTS and actual audio remain separate verification gates. This is **a facial expression/gesture fix**, not improved synthetic speech generation, audio quality or voice selection.

For integrator #663, treat the two changes as independently cherry-pickable:
- `64535f994f8e1e420dd5187f928d361e1525f331`: work-alive holographic raster.
- `2a41a826fea68012a869c92c3b5d39b81634000c`: Avatar sentence-boundary gesture correction (parent includes first commit and documentation).

**Avoid merging the entire graphic branch blindly** into a release-frozen PR. The second commit also has the first as an ancestor; cherry-picking it alone adds its own three files only. For exact verification, run `AvatarSentenceAccentPolicyTest`, `AvatarSpeechBoundaryTest`, `AvatarSpeechOrderingRegressionTest`, `AvatarSentenceEndGestureTest` if present, then the full source-exact suite.
