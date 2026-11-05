# H4-DIALOG-HANDOFF — 2026-11-05

Status: WIP / UNVERIFIED, test-source checkpoint only. This is not an accepted Android fix.

## Source and purpose
- Repository: Ullmann27/device-ai.
- Independent branch: support/h4-avatar-stop-next-turn-regression-20261105.
- Frozen base: PR #544 at 93b92a33e583ba3d88079cb24325d7b00c8c5714; base Git tree 3a3691b36c6f843334597caacc79905e3517d913.
- Existing direct comparison: #526 at 5a18f74f18d9b7d927f8c622052369496153829f, #527 at 19039e5b822310cd5ab1ce5cc5cb854822a1bcd3.
- Existing parallel owner: #545 (Activity/TTS/camera), C1 #519 sole shared integrator. Do not replace their work.
- Original supported TTS listener, reducer, single live view and renderer remain untouched.

## New bounded coverage
New single test file: app/src/uiTest/java/at/heinz/deviceai/AvatarStopNextTurnUiTest.kt.

Two cases invoke the actually installed AgentActivity TextToSpeech progress listener with deliberately synthetic PCM and word callbacks: current onStop(interrupted=true/false), immediate next answer, delayed old-ID start/begin/audio/range/stop/done/error, and final STOP of the second answer. Assertions inspect the real live ContinuumHologramView's AvatarPoseReducer and Active ID, not a duplicated helper state machine. This does not assert self-running speech recognition or audible user speech.

This fills the narrowly missing current-onStop -> next-turn callback regression identified in the existing #547 PCM intake; it must not be confused with the existing previous-onDone -> next-turn test or the broader speech-loop acceptance Q1.

## Source-bound verification
Read exact production Git blobs at frozen base:
- AgentActivity.kt 9ad943b9dc63c6692a036cda28cc13f0d1abc3d7.
- TtsHologramSignal.kt c8dcbcb54410c7c8b48fcf12cf0b1196a2fe8eb3.
- AvatarDirector.kt 641ebbc3586b95b28c037e152e013ede3572a450.

Executed seven V8 text/source contract assertions against those blobs: 7/7 PASS. They verify callback ID checks, STOP reset, Director old-ID rejection, and that this precise base still lacks the measuredLevel availability sentinel. These are STATIC checks, not Kotlin compilation, Robolectric or hardware tests.

Required actual test after making a complete checkout and obtaining the existing shared build lease:

```sh
./gradlew --offline --no-daemon --max-workers=1 -PcontinuumUiWidgetTests=true :app:testDebugUnitTest --tests at.heinz.deviceai.AvatarStopNextTurnUiTest
```

Status: Kotlin compilation NOT_RUN; new JUnit/Robolectric tests NOT_RUN; source-bound RED/GREEN NOT_RUN; device model, audio, native GPU, Android APK NOT_RUN. Container and local Python execution tools returned infrastructure errors during this invocation. No alternative toolchain or Android device was falsely assumed.

## Remaining blocker and next entry
- #544 and #545 at this intake still use TtsHologramSignal.level(Float) without the nullable measuredLevel() from #526, so known silence may activate word-range fallback. #545 owns the selective four-file donor intake; never wholesale replace the shared Activity, reducer or Director.
- Natural automatic turn taking remains independently FAIL/changes-required in Q1, as the normal ASR path only fills a draft and TTS completion does not reopen listening. C1/#545 own consent-bound opt-in and owner/tool gate integration.
- When the owner team has the complete Android checkout, execute the two new unchanged cases on the pinned source, verify source/manifest and all historical guards, and only then adopt the test file into C1. Keep previous Android regressions unchanged. Report any RED with the exact original stack trace; this standalone test is NOT a passed gate.
- No application production files have been modified. No device-based recognition or audio-lip synchronization is claimed.

## Boundaries and rollback
EXP-05, EXP-11, EXP-12 relevant. No new model, audio route, camera permission, consent bypass, files/owner data, offline switch, GLB, orb, Gradle, signing, package or runtime change. Branch can be excluded; withdrawing the one new regression file and this handoff restores the exact base. No main merge, Actions, paid runner, sign, release or install.

## H4 independent measured-PCM RED→GREEN host experiment — 05.11.2026

This subsequent test-only checkpoint does **not** modify the previously frozen UI regression or any production source. Revalidated the exact published heads: #519 `13c37f55789bf67b07beeb37cc1a5035526e623e`, #527 `19039e5b822310cd5ab1ce5cc5cb854822a1bcd3`, #526 `5a18f74f18d9b7d927f8c622052369496153829f`, #535 `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a`, #539 `766a1953fe1b72a890448e38178448d50140fa40`; followed newer common #544 `8a903fbebe605716b6163da35e1c3b2129af40a4` and dialogue owner #545 `8515a455be34e5b066c631cea9903a1eddc634d4`. C1 retains ownership of Activity/reducer/voice integration. The original signal blob is *identical* on #544 and #556.

Host reproducibility:
1. Extract original `TtsHologramSignal.kt` from #544 (`c8dcbcb54410c7c8b48fcf12cf0b1196a2fe8eb3`) and `HologramAudio.kt` from #526 (`313530d45824a01ccf3b551891c71f327ecb0403`).
2. Extract donor `TtsHologramSignal.kt` from #526 (`8f3da133e8d7532c32d2cb5c45ab9bcd0ef8f294`); retain the exact same audio helper. Verify all three with `git hash-object`; the execution copies matched GitHub's fetched blob identifiers exactly.
3. Compile each pair with `kotlinc HologramAudio.kt TtsHologramSignal.kt MeasuredPcmHost.kt -include-runtime -d voice.jar`; run `java -jar voice.jar`. Actual JDK 21.0.12.1 and Kotlin 1.9.0 (host, no Android).
4. Original: Kotlin compile exit0, **1 PASS/1 expected FAIL**, process exit1 because the nullable `measuredLevel` API is missing; the legacy `level()` returns 0 both for missing PCM and measured silence. Donor: Kotlin compile exit0, **12/12 PASS**, process exit0: no PCM, pre-anchor, silent PCM, nonzero PCM, end-of-data, wrong/stale IDs, new speech and stop are distinguished.
5. Raw logs, source identities and SHA-256 in `docs/evidence/h4-dialog-20261105/pcm-host-20261105.json`; runnable independent harness `tools/h4-voice/MeasuredPcmHost.kt`. No simulated Kotlin business-class replacement; these two exact production files were actually compiled. This is nevertheless **only a host-signal test**, not an Activity/reducer, TTS-engine, microphone, native avatar, or Fold7 test.

**Release gap remains:** newer #544's published source has the same original `TtsHologramSignal` blob; host donor success is evidence that the nullable contract is implementable, *not* evidence that C1 has integrated measured silence into its frame/Director/Reducer callers. No shared production files were edited. #545/C1 must selectively integrate the entire linked caller contract, and the existing unchanged `AvatarStopNextTurnUiTest` must still be compiled and run on the frozen common Android source. Newer #544 reports 3,133 PASS/19 SKIP on its own source, not an acceptance of this separate PCM donor or our still-unexecuted UI tests. Do not merge this experimental proof as an app fix or change package/signing/offline/owner authority.
