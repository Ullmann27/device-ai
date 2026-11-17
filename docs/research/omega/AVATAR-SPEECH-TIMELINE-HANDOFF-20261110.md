# Avatar speech timeline — bounded milestone

Integration owner: Cinema root integrator. Target: `cinema/fluid-light-stage-20261110` / PR #715. Worktree: `fix/avatar-speech-timeline-20261110`, from local `4ac9c9b94b9fb5e391646e2e99e986db32ec7997`; the corresponding root checkpoint is published as `ae3ce34b5aff285ed8c569ca2844975e34131a86`. This worktree does not modify the root checkout or PR. Publication remains with the integrator.

## Reproduced defect and correction

When TTS gives neither PCM nor word ranges, the existing, explicitly labelled `TEXT_PACE_FALLBACK` estimates word times from the admitted audible start. A delayed `voiceFrame` previously stamped all caught-up visemes and sentence endings with delivery time. A first frame at +470 ms restarted the first word at SIL instead of reaching AA; a +1700 ms stall restarted an already expired sentence gesture.

`AvatarDirector.textPaceLevel` now stamps both event types with the original estimated word time: utterance start + 250 ms grace + word index × 360 ms. No new fallback, timer, mouth loop, rendering engine or audio inference was added. The fallback remains an estimate, never measured audio. Real PCM/range callbacks retain precedence.

Actual call path: `AgentActivity.installVoiceProgressListener.onStart` admits the current utterance → scheduled `voiceFrame` reads `TtsHologramSignal` → `AvatarDirector.audioLevel` → existing fallback only when PCM/ranges are absent → `AvatarPoseReducer` expires cues/gestures → the shared `ContinuumHologramView` pose reaches `FilamentAvatarRenderer` and `AvatarMorphMapper`. Desktop and phone keep this same event path.

## Verified boundaries

- RED: original Director + two new tests: **8 tests, 2 failures**. Expected AA / actual SIL; expected sentence timestamp 10250 / actual 11700.
- GREEN: **67 tests, 0 failures** across the 11 classes below, compiled from actual Kotlin sources with Kotlin/JUnit; no source extraction or renderer shim.
- Existing measured PCM/look-ahead, stale utterances, word admission, STOP, reattachment and fallback completion tests pass.
- Punctuation cache clearing is a deliberate existing admission contract and remains unchanged.
- ASR barge-in and `onPause` already call `stopVoiceAnimation`; pause also stops TTS, clears signal state and cancels callbacks. Source audit only; no Android lifecycle claim from this milestone.
- Original GLB unchanged: `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`, same 21-target contract. No asset/material/camera/scene/quality edits.
- Applicable register boundaries: EXP-07 regression evidence, EXP-12 lifecycle/replay compatibility. No model training, memory promotion or external service.

`PORTIERT`: source patch ready for integrator. `VERIFIZIERT`: host logic tests only. `GERÄTEBESTÄTIGT`: **NOT_RUN**. Android build, actual audible TTS, Fold7 timing/GPU and desktop native rendering: **NOT_RUN**. No APK, signing, installation, main merge or new dependency.

## Reproduction

Run from the checkout with an existing Kotlin compiler, JUnit 4.13.2 and Hamcrest 1.3:

```bash
AVATAR_KOTLINC=/workspace/scratch/5900afa6af29/qa-installed-tools/kotlinc/bin/kotlinc
AVATAR_JUNIT=/workspace/scratch/5900afa6af29/qa-installed-tools/gradle-8.11.1/lib/junit-4.13.2.jar
AVATAR_HAMCREST=/workspace/scratch/5900afa6af29/qa-installed-tools/gradle-8.11.1/lib/hamcrest-core-1.3.jar
AVATAR_CLASSES=(
  at.heinz.deviceai.TtsHologramSignalTest
  at.heinz.deviceai.avatar.AvatarSpeechReattachmentTest
  at.heinz.deviceai.avatar.AvatarSpeechAdmissionTest
  at.heinz.deviceai.avatar.AvatarSpeechOrderingRegressionTest
  at.heinz.deviceai.avatar.AvatarSpeechClockTest
  at.heinz.deviceai.avatar.AvatarSpeechBoundaryTest
  at.heinz.deviceai.avatar.AvatarTextPaceFallbackTest
  at.heinz.deviceai.avatar.AvatarExistingContractRegressionTest
  at.heinz.deviceai.avatar.AvatarDirectorTest
  at.heinz.deviceai.avatar.AvatarNativeRegressionTest
  at.heinz.deviceai.avatar.AvatarSentenceAccentPolicyTest
)
AVATAR_TEST_FILES=()
for name in "${AVATAR_CLASSES[@]}"; do AVATAR_TEST_FILES+=("app/src/test/java/${name//.//}.kt"); done
AVATAR_JAR=$(mktemp --suffix=.jar)
"$AVATAR_KOTLINC" \
  app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt \
  app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt \
  app/src/main/java/at/heinz/deviceai/avatar/AvatarSentenceAccentPolicy.kt \
  app/src/main/java/at/heinz/deviceai/HologramAudio.kt \
  app/src/main/java/at/heinz/deviceai/TtsHologramSignal.kt \
  "${AVATAR_TEST_FILES[@]}" -classpath "$AVATAR_JUNIT" -include-runtime -d "$AVATAR_JAR"
java -cp "$AVATAR_JAR:$AVATAR_JUNIT:$AVATAR_HAMCREST" org.junit.runner.JUnitCore "${AVATAR_CLASSES[@]}"
rm "$AVATAR_JAR"
```

Verified runtime SHA-256: `074b60fba0bbf963631d50069e7d4b38fba3f33dbe946a4b6b40aeec5c21056b` (`AvatarDirector.kt`); regression file SHA-256: `c0fc23c4bb40a57d50c9063744ae148222f49dd043c094d28b1d946fa29f0617` (`AvatarTextPaceFallbackTest.kt`).

Next: integrate this isolated patch, run the existing Android caller checks under the root build lease, then check an actual no-range/no-PCM engine with a delayed main frame on device. Rollback: revert this milestone commit; the prior labelled fallback remains functional but delivery-time-biased.
