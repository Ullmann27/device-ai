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
