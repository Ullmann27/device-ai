# Support trio — selective PCM and camera handoff

Date: 2026-11-05. Status: **SOURCE_REVIEW_ONLY / ANDROID_NOT_RUN / FOLD7_NOT_RUN**.

Owner of the production integration: [#545](https://github.com/Ullmann27/device-ai/pull/545), combined in [#544](https://github.com/Ullmann27/device-ai/pull/544). This support contribution changes no production files.

## Exact source and result

Latest reviewed combined candidate: `08aa76e8393bfedfeb1274c4e292b7e2a3139d15`. The avatar assistant fetched all four current production files at this exact SHA. Root independently fetched and inspected all four donor sections. Donor is [#526](https://github.com/Ullmann27/device-ai/pull/526), `5a18f74f18d9b7d927f8c622052369496153829f`.

**The four-stage measured-silence connection is still absent on the reviewed combined candidate.** Signal, Director and Contract are blob-identical to the earlier #543 baseline. AgentActivity now contains conversation-camera changes but its voiceFrame still reads non-null level() and calls the two-argument audioLevel().

This is a source-level finding. No audible TTS, live mouth animation, Android test or device result is claimed by this review.

## Selective donor intake

| Current file | Current blob | Donor blob | Required behavior |
| --- | --- | --- | --- |
| [TtsHologramSignal.kt](https://github.com/Ullmann27/device-ai/blob/08aa76e8393bfedfeb1274c4e292b7e2a3139d15/app/src/main/java/at/heinz/deviceai/TtsHologramSignal.kt) | `c8dcbcb54410c7c8b48fcf12cf0b1196a2fe8eb3` | [`8f3da133e8d7532c32d2cb5c45ab9bcd0ef8f294`](https://github.com/Ullmann27/device-ai/blob/5a18f74f18d9b7d927f8c622052369496153829f/app/src/main/java/at/heinz/deviceai/TtsHologramSignal.kt) | Add utterance-bound nullable measuredLevel(id, now). null means unknown/not started/wrong ID/no PCM; 0f means known silence, including exhausted known PCM. Retain level() compatibility. |
| [AgentActivity.kt](https://github.com/Ullmann27/device-ai/blob/08aa76e8393bfedfeb1274c4e292b7e2a3139d15/app/src/main/java/at/heinz/deviceai/AgentActivity.kt) | `9ad943b9dc63c6692a036cda28cc13f0d1abc3d7` | [`9fffd9ea9857c7f1b2462832b7ef95e5ae8b888a`](https://github.com/Ullmann27/device-ai/blob/5a18f74f18d9b7d927f8c622052369496153829f/app/src/main/java/at/heinz/deviceai/AgentActivity.kt) | Connect the real voiceFrame: read measuredLevel for voiceUtteranceId at elapsedRealtime, display measured ?: 0f, and pass measuredPcmAvailable = measured != null. |
| [avatar/AvatarDirector.kt](https://github.com/Ullmann27/device-ai/blob/08aa76e8393bfedfeb1274c4e292b7e2a3139d15/app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt) | `641ebbc3586b95b28c037e152e013ede3572a450` | [`34df8451c6370d99c06fb53c7731d1be019fa561`](https://github.com/Ullmann27/device-ai/blob/5a18f74f18d9b7d927f8c622052369496153829f/app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt) | Extend audioLevel with measuredPcmAvailable. Use word-range fallback only when PCM is absent. Known zero must emit PCM_ENVELOPE zero. |
| [avatar/AvatarContract.kt](https://github.com/Ullmann27/device-ai/blob/08aa76e8393bfedfeb1274c4e292b7e2a3139d15/app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt) | `42a92a5f57512344a403a5910daab07a93bf7154` | [`5b6479f2cc6d1ee7cf962cb4337bcd02c48110be`](https://github.com/Ullmann27/device-ai/blob/5a18f74f18d9b7d927f8c622052369496153829f/app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt) | Reducer records lastAudioSource/lastMeasuredLevel. Measured zero or expired measured audio closes jaw, viseme and weight; clearSpeech resets the added fields. |

The distinction must travel through all four stages. Changing the signal alone leaves the Activity caller unchanged. Changing only Director still allows the old reducer to keep an active vowel cue. Zero and unavailable PCM are different states; the no-PCM TTS engine fallback must remain available.

The donor caller is:

```kotlin
val utteranceId = voiceUtteranceId
val measured = utteranceId?.let { voiceSignal.measuredLevel(it, android.os.SystemClock.elapsedRealtime()) }
val level = measured ?: 0f
hologramView?.setAudioLevel(level)
utteranceId?.let { avatarDirector.audioLevel(it, level, measuredPcmAvailable = measured != null) }
```

Apply selective hunks to the newest combined source. Do not replace the whole older AgentActivity, Contract or renderer. Preserve the new camera consent/STOP controls, C1 contracts, corrected head and blink, and the AvatarExpression.WARM compatibility fix in 7ecdafac328700295da393ce44bbe22eb8b21aee.

The renderer path already exists: ContinuumHologramView.onAvatarEvent → AvatarPoseReducer → onDraw pose → native3d.render → FilamentAvatarRenderer → AvatarMorphMapper.update. The current mapper already clears visemeBlend for SIL/zero cue. It does not need replacement from the older donor to add this PCM connection.

## Existing coverage to reuse

| Donor test file | Blob | Scope |
| --- | --- | --- |
| [AvatarMeasuredSilenceTest.kt](https://github.com/Ullmann27/device-ai/blob/5a18f74f18d9b7d927f8c622052369496153829f/app/src/test/java/at/heinz/deviceai/avatar3d/AvatarMeasuredSilenceTest.kt) | `1d60e46c2293639bc2db89dddf22f05bb72e0664` | Six existing tests; first three cover Signal → Director → Reducer → Mapper. |
| [AvatarSpeechPipelineTest.kt](https://github.com/Ullmann27/device-ai/blob/5a18f74f18d9b7d927f8c622052369496153829f/app/src/test/java/at/heinz/deviceai/avatar3d/AvatarSpeechPipelineTest.kt) | `f254cb9df6bae6a69ad469e5f3b0c211ca5215c9` | playbackPcmAndWordRangesDriveTheMouthAndStopRejectsLateCallbacks. |
| [AvatarTtsLiveConnectionUiTest.kt](https://github.com/Ullmann27/device-ai/blob/5a18f74f18d9b7d927f8c622052369496153829f/app/src/uiTest/java/at/heinz/deviceai/AvatarTtsLiveConnectionUiTest.kt) | `6a3cf3a88d54a15939f2504a0bcf025d89e4e410` | Measured silence and no-PCM fallback through actual listener; stale/done IDs, reparenting and pause. |
| [AvatarSpeechOrderingRegressionTest.kt](https://github.com/Ullmann27/device-ai/blob/5a18f74f18d9b7d927f8c622052369496153829f/app/src/test/java/at/heinz/deviceai/avatar/AvatarSpeechOrderingRegressionTest.kt) | `483d2dc036469dad1d173bea25ea51c9de385758` | Reconcile old expectations that retain vowel cues after measured audio expires; new expected pose is SIL. |

AvatarMeasuredSilenceTest contains:

1. silentPcmClosesAnActiveVowelWithoutFallbackMotion
2. exhaustedPcmRemainsKnownSilenceDuringAWordCallbackWindow
3. missingPcmKeepsTheExistingWordRangeFallback
4. measuredSilenceCanResumeTheStillCurrentWordCue
5. expiredMeasuredAudioCannotLeaveAQueuedVowelOpen
6. staleUtteranceCannotClaimCurrentPcmAvailability

One precise additional listener case is useful: current-utterance onStop(current, true/false), then late PCM/word callbacks, then a new utterance. The existing old-ID onStop and current-ID onDone cases do not establish the full interruption sequence. No duplicate or unexecuted test class was added by this support team.

STOP already clears the director utterance, current ID, signal, scheduled frame and displayed level; late IDs are rejected. Preserve this lifecycle and use the existing TTS listener.

## Camera and personal recognition

The [new Activity integration](https://github.com/Ullmann27/device-ai/blob/08aa76e8393bfedfeb1274c4e292b7e2a3139d15/app/src/main/java/at/heinz/deviceai/AgentActivity.kt) explicitly asks for camera consent, explains face-position tracking, supplies a conversation STOP control and requires a fresh start after leaving the app. Root read the complete commit patch for these changes. Runtime behavior remains unverified by this team.

The reviewed Camera2 input provides face position, confidence and timing. No enrolled personal-identity template or recognition of Heinz was established. Face tracking and device-unlock evidence must not be presented as identity recognition. The new dialog correctly states that personal facial recognition is not available.

Memory retrieval, complete spoken conversation quality and recognition enrollment were not newly executed or implemented in this support review. Their behavior must be assessed on the final combined source, with device acceptance separate from host tests.

## Handoff and execution boundary

[Exact PCM handoff comment](https://github.com/Ullmann27/device-ai/pull/545#issuecomment-6652368998). This document provides intake instructions and existing regression references, not a claim that the donor has been integrated or its tests have passed on #544.

Root's separate Node/backend baseline results apply only to #543 89ae402d88b80ca5c48ab241e20c0369b7db517d. They do not validate these Android source changes.
