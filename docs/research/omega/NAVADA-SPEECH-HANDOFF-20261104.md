# NAVADA: speech boundaries and mouth timing

Date: 2026-11-04. Implementation base: `6cb9e760f0a16a6f34a8442abf9b79dcf2705799`.

This change extends the existing `AvatarDirector` → `AvatarEvent` →
`AvatarPoseReducer` → `AvatarMorphMapper` path. It adds no competing avatar or
conversation state machine. The packaged GLB path and its 21 named morph targets
are unchanged by the speech work.

## Implemented behavior

- **Bilabials:** an active `PP` cue immediately clears outgoing vowel morphs,
  selects `PP`, and suppresses both residual `JawOpen` and inner-jaw rotation.
  This applies even when the audio envelope remains high or the render timestamp
  is repeated. The existing authored-opening compensation remains in force for
  other visemes, including outgoing vowel blends.
- **Render time:** an ordinary running viseme transition advances with elapsed
  seconds, retaining the existing 70 ms linear blend. Calling it twice at the
  same instant cannot advance that blend. A reset/backward clock or a gap over
  one second initializes the current target. STOP still clears speech in the
  reducer; silence clears viseme blends immediately. The speech audio envelope
  can still animate the jaw when a word/phoneme cue is unavailable.
- **Audio envelope:** the nominal existing coefficient of `0.4` per 33 ms is
  converted to `alpha(dt) = 1 - exp(ln(0.6) * dt / 33)`, with `dt` in milliseconds.
  The first accepted sample retains the calibrated `0.4` response. Subsequent
  constant input over equal elapsed time has equal response regardless of the
  number of intervening callbacks. The existing dead zone, gain, ordering and
  stale-audio timeout remain in effect.
- **Event isolation:** duplicate starts of the current utterance do nothing.
  Superseded and ended IDs enter a bounded 128-ID recent-terminal guard. Wrong
  utterance cues/stops are discarded before they can change the reducer's
  default pose clock. The Activity's current unique TTS-ID admission check is
  still authoritative; the bounded guard is additional defense, not an unlimited
  history of all IDs ever seen.
- **Range ordering:** `wordRange(id, start, end, audioFrame = frame)` accepts the
  existing TTS range callback with an optional frame watermark. A backward text
  start, a repeated range or a non-growing range at the same start is ignored.
  Negative or backward audio frames are ignored. Distinct forward text ranges
  may have equal audio frames. The watermark resets for each new utterance.
  Invalid callbacks do not advance it. The matching Activity hook is present in
  `installVoiceProgressListener`.

`onRangeStart` supplies text ranges and an audio-frame marker. This change uses
the marker for ordering only. German graphemes are still approximated as visemes
over the existing coarse word duration; these are **not engine phoneme timing
marks** and are not a measured sample-accurate speaker clock.

## Integration preserved

The existing microphone and typed-message paths stop TTS and the avatar's speech
animation for barge-in. Their unique-utterance checks are preserved. No unrelated
background worker is canceled by these changes.

The real LiteRT stream produces text deltas before the current answer quality
gate. This patch does not speak those uncertified deltas or install a second
sentence-speaking pipeline. Sentence staging, engine-supported synthesis timing
and end-to-end latency measurement remain separate integration work; a first
audio target below 1.5 seconds has not been demonstrated here.

## Regression expectations intentionally changed

| Existing test file | Exact reason for the changed expectation |
| --- | --- |
| `AvatarMorphTransitionTest.kt` | AA→PP previously required residual AA and a measured open jaw. Both must now be zero at the bilabial seal, including cue-only and reduced-motion-resume cases. Repeated timestamps no longer snap a blend; clock rollback still does. AA→O compensation and PP→AA interpolation remain checked. |
| `AvatarSpeechOrderingRegressionTest.kt` | Two second-sample energy expectations change from `0.256` to `0.21990544`, because the samples are 50 ms apart rather than the nominal 33 ms. Replay rejection, timeout, future-time rejection and after-end checks remain. |
| `AvatarDirectorTest.kt` (`AvatarPortedPartsTest`) | Saturation/noise fixtures now deliver samples 33 ms apart instead of relying on twelve 1 ms callbacks for saturation. Cue/end times move to 400/450 ms to stay chronological; assertion thresholds are unchanged. |
| `Avatar3dContractTest.kt` | The name-binding test uses a fresh mapper per viseme. Reusing one mapper for different targets at fixed time zero mixed binding with the obsolete snap-on-equal-time policy. All binding/weight assertions remain. |

New regression files are `AvatarSpeechBoundaryTest.kt`,
`AvatarSpeechClockTest.kt` and `AvatarArticulationBoundaryTest.kt`. They exercise
recent terminal-ID replay, active-start idempotency, supersession, text/audio
ordering and tied frame markers, foreign-event clock isolation, sample-cadence
independence, AA→PP closure, render-time idempotency and render-cadence equivalence.

## Executed evidence

The focused runner compiled the real production `AvatarContract`,
`AvatarDirector`, `AvatarGlbContract`, `AvatarMorphMapper` and `AvatarIdleMotion`
plus **all** test sources in the `avatar` and `avatar3d` test packages with Kotlin
2.4.0/JDK 17. JUnit 4.13.2 ran from the repository root and the asset tests read
the actual packaged GLB. No replacement Android or production-class stubs were
used for this runner.

| Stage | Observed result |
| --- | --- |
| Added regression tests before the three production fixes | Compilation succeeded; 13 tests ran and 11 failed. |
| Final focused production-and-tests run | Compilation succeeded; **74 tests passed**, zero failures. |

Raw outputs and exact compiler/JUnit commands are in
[`docs/evidence/navada-speech-20261104`](../../evidence/navada-speech-20261104).
The recorded commands contain the actual local tool paths of this run.

These checks verify Kotlin logic and the asset contract. They do not establish
device frame rate, visible lip contact under the final renderer, acoustic
lip-sync error, lifecycle behavior on a Fold device, or the complete Android
integration. The separate build worker owns the combined Gradle/SDK and emulator
verification; its report must be consulted before making broader claims.
