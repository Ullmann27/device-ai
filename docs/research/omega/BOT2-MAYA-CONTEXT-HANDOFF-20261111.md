# Maya / conversation integration

Base: `fd1de22cf3f6ed159601c9b944cc25c86f0fc221`, integration PR #747.
Target: `integration/continuum-final-visibility-20261111` through the main integrator.
Dependency: voice bot commit `9f53e69f38ec5a7ca4eff8b435bc5e10615fbe63`
adds `NetworkTurnConstraint.forbidsExternalSpeech`.

## Repaired behavior

- Maya cancellation suppresses stale callbacks by design. Previously TON off,
  voice selection and key changes therefore left hands-free in SPEAKING.
  Explicit owner stops now call the existing conversation state machine
  synchronously. Automatic reply replacement and chunk transitions keep their
  established behavior.
- Explicit offline/cloud-transport vetoes reach the shared speech selection.
  Local-model and private-bot responses capture the original request's veto
  before asynchronous work. A later allowed request cannot authorize an older
  forbidden response. Busy status queries cannot clear a pending veto.
- Normal answers, context retention, model runtime, the approved chunk queue,
  avatar callbacks and original assets remain the existing shared pathways.
  No new model, memory store or agent loop was introduced.

## Verification

Baseline composed the actual `MayaSpeech` adapter with the actual
`ContinuousConversationMode`. During playback, provider stop returned without
an end callback; assertion failed: expected PAUSED, actual SPEAKING (1 test,
1 failure). This is a host integration reproduction, not an Android Activity
or physical device run.

After repair: 21 Kotlin/JUnit tests passed across
`MayaConversationInterruptionTest`, `ConversationSpeechIntegrationRegressionTest`
and `ContinuousConversationModeTest`. Includes 20 consecutive multi-chunk
reply turns, no-callback interruption, stale callback rejection, and explicit
microphone resumption. Android/voice/avatar source contracts: 15 passed.
The privacy predicate/provider tests are in the dependent voice-bot commit.

Host Kotlin 2.2.21 / JDK21.0.13; tests used the repository's actual production
classes plus JUnit4.13.2. A pure host check does not confirm Android ASR,
audible playback, German pronunciation, native lips or device thermal behavior.

Combined Android compilation, full QA and ARM64 APK: pending main integration.
Required suite: `./gradlew :app:testDebugUnitTest` including the new
`at.heinz.deviceai.MayaConversationInterruptionTest`.
Targeted source checks:
`node --test .github/continuum/continuous-conversation-wiring.test.mjs .github/continuum/m5-voice-duplex-wiring.test.mjs .github/continuum/android-avatar-wiring.test.mjs`.

Status: PORTIERT locally; host VERIFIZIERT as above; GERÄTEBESTÄTIGT NOT_RUN.
At this checkpoint no remote push was claimed. Main integrator owns connector
publication into #747 and records the remote commit separately.

## Compatibility and rollback

EXP-05 local-runtime boundaries and EXP-14 context research defaults remain
unchanged. Remove-only voice privacy enforcement grants no network access.
Internet OFF/ASK, key storage, signing, package identity, main and release gates
remain unchanged. Revert this focused change together with its tests if needed;
do not delete donor branches. New tests should remain required in pinned QA.

## Timestamp callback extension

Basis: `d006963764223a2c05f4cec21b5f0ddca0a27383`.
Depends on the voice bot's additive `MayaSpeech.Listener.range` and
`MayaSpeechAlignment` implementation; integrate these together.

The Activity forwards validated provider word ranges to the existing
`UtteranceProgressListener.onRangeStart`. Its current-utterance/foreground
guards, `AvatarDirector.wordRange` admission and `TtsHologramSignal.anchor`
remain unchanged. The frame is a measured mono playback sample, not a byte
offset or a download timer. No extra queue, renderer or animation timer.

Executed against the actual voice-bot timing sources: 21 Kotlin/JUnit tests
passed across `MayaAvatarTimingIntegrationTest`, `TtsHologramSignalTest` and
`AvatarSpeechBoundaryTest`. The new integration tests exercise measured PCM
and word callbacks, expired words after UI delay, and stopped/replaced
utterances. Ten Activity/Avatar wiring contracts passed. Initial fixture
expectations were corrected for the existing 40 ms RMS windows; those fixture
failures were not application regressions.

This is host verification. Combined Android build, real provider response,
audible output and Fold7 acceptance remain with the main integration run.
Word boundaries are provider-derived; visemes inside each word remain the
existing text-based German approximation, not measured provider phonemes.

Read-only input finding: `AndroidOnDeviceSpeechInput` uses the on-device
Android recognizer; `onBufferReceived` is unused. The current input path
contains no `AudioRecord`, `MediaRecorder` or retained microphone file for
ElevenLabs noise isolation/ASR. No recorder, upload or cloud-ASR requirement
was added. User speech continues through the existing local recognizer.
