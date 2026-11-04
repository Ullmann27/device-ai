# TEST_RESULTS – jarvis-grafik/avatar-ref-match (Jarvis Avatar, Grok Bot)

Base: PR #478 head `fix/continuum-device-experience-resume-20261104` @ d8fd7f7c (continuation of #477; #477 itself contains only docs),
plus cherry-pick of JarvisDesign/JarvisNeon (#479, b085cf78) and merge of Jarvis Kern `design/jarvis-cockpit-home-20261104` @ cf0651d5.

Command (JDK 17, /opt/android-sdk, host Robolectric NATIVE graphics):
```
./gradlew -PcontinuumUiWidgetTests=true :app:compileDebugKotlin :app:testDebugUnitTest \
  --tests 'at.heinz.deviceai.avatar.*' --tests 'at.heinz.deviceai.avatar3d.*' --tests 'at.heinz.deviceai.ui.*' \
  --tests 'at.heinz.deviceai.cockpit.*' --tests at.heinz.deviceai.ContinuumAvatarNativeUiTest \
  --tests at.heinz.deviceai.ContinuumReferenceCockpitUiTest --tests at.heinz.deviceai.ContinuumHologramMotionUiTest \
  --tests at.heinz.deviceai.ContinuumAvatar3dLifecycleUiTest --tests 'at.heinz.deviceai.ContinuumAdaptive*' \
  --tests at.heinz.deviceai.ContinuumTaskEffectsUiTest
```
Result: compile OK; 151 tests, 147 passed, 4 failed – all 4 PRE-EXISTING on the base d8fd7f7c, not caused by this branch:
- ContinuumHologramMotionUiTest.differentOrbitalPhasesActuallyChangeRenderedPixels (re-run on clean d8fd7f7c worktree: also FAILED)
- ContinuumAdaptiveCockpitLayoutTest.everyHeaderAndActionHasUnscaledFortyEightDpTouchArea (47.999992 dp float rounding)
- ContinuumCoreMirrorTest.allEightControlsKeepEqualBoundsAndBalancedRows, …actionTextUsesReadableDestinationNamesOnBothSides
  (both reported red on d8fd7f7c in CHATGPT-477-STAND.md)

New / affected tests, all green:
- ContinuumAvatarNativeUiTest 3/3 (speech opens mouth in mouth region, interruption returns to identical closed frame)
- avatar.ContinuumAvatarLookPreviewUiTest 1/1 (idle/speech/gaze/wake differ, luminous output) – writes preview PNGs
- ui.JarvisSequenceEffectsUiTest 1/1 (command fly-in, card stack, reported outcomes, max 4, dismiss/clear, id validation)
- ui.JarvisSequenceViewDockingUiTest 1/1 (CockpitAnimationBus → view, stale turn dropped, TurnEnded flies out)
- AvatarNativeRegressionTest 4/4, JarvisShellLayoutTest 5/5, cockpit.* green

Not verified: Filament/GPU rendering, physical phone/Fold7, real TTS timing. Previews are host Android Canvas (Robolectric), not device screenshots.

## Step 3 (brain orb lightning + Muster E data flight) – rerun
`--tests 'at.heinz.deviceai.ui.*' 'avatar.*' 'cockpit.*' ContinuumAvatarNativeUiTest ContinuumHologramMotionUiTest`:
only the same 4 pre-existing failures; JarvisSequenceEffectsUiTest (now incl. data flight + orb lightning render) green.
JarvisMotion/JarvisNeon/JarvisDesign taken unchanged from #479 tip c617e1ab (file checkout, no edits).

## Step 4 (Muster F envelope, G question pills, H alarm) – rerun
`--tests 'at.heinz.deviceai.ui.Jarvis*' 'avatar.*' 'cockpit.*' ContinuumAvatarNativeUiTest` + compileDebugKotlin: BUILD SUCCESSFUL, all green.
(Wider `ui.*` run: only the 3 pre-existing AdaptiveCockpit/CoreMirror failures.)

## Step 5 (Filament path + AvatarMorphMapper) – rerun
`compileDebugKotlin` + `avatar3d.*`, `avatar.*`, ContinuumAvatar3dLifecycleUiTest, ContinuumAvatarNativeUiTest: all green,
incl. new Avatar3dContractTest.visemesCrossfadeOverSeventyMillisecondsAndBlinkLastsFifteenHundredths.
Filament changes (bloom .35, iris/pupil hidden via layer mask, emissive glow from TTS envelope, idle float + Z drift)
compile only – NOT verifiable on host (no GPU); needs device check.
