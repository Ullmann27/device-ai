# Maya original-text word timing

Base: `d006963764223a2c05f4cec21b5f0ddca0a27383`, the already-integrated
Code53 voice/graphics/cancellation fixes. This is a separate next candidate.

## Existing connection extended

The canonical answer queue still supplies each approved text chunk. Maya uses
the same selected voice ID, `eleven_multilingual_v2`, settings and PCM 24 kHz
mono format. The single request now uses `/with-timestamps`, returning base64
audio plus original-character alignment. The old path already buffered each
complete chunk; this adds no second synthesis call, playback engine or queue.

`ElevenLabsPcmRequest` bounds both JSON and decoded PCM, rejects HTTP errors,
redirects and malformed audio, and preserves permission/cancellation checks.
Only alignment whose concatenated characters equal the exact approved text is
admitted. Unicode characters map to original UTF-16 text offsets. Timing must
be finite, ordered and within the actual audio duration. Missing or malformed
alignment preserves valid PCM playback with no fabricated word offsets;
`normalized_alignment` is deliberately not substituted.

`MayaPcmPlayer` reports actual `AudioTrack.playbackHeadPosition` through an
additive progress callback. The existing provider emits each active word once,
skips already-expired words, and rechecks the freshest observed playback frame
when a queued UI callback runs. STOP, replacement and consent revocation still
discard old callbacks. No download clock or independent animation timer is used.

The companion Activity patch forwards `MayaSpeech.Listener.range` directly to
the existing utterance listener, `AvatarDirector.wordRange` and PCM anchor.
The avatar's existing German text-to-viseme approximation remains an
approximation. These are provider-supplied character/word times, not measured
phonemes or new face morphs. Actual PCM continues to drive amplitude.

## API and compatibility

Official API verified 2026-11-11:
https://elevenlabs.io/docs/api-reference/text-to-speech/convert-with-timestamps
The `alignment` object describes original text; `normalized_alignment`
describes normalized text. The API explicitly excludes `language_code` for
`eleven_multilingual_v2`; no ineffective German override was added.

`Request.synthesize` and the old three-argument `Player.play` remain compatible.
The new methods have defaults preserving existing provider/test implementations.
There is no additional conversation history or private context in the request.

## Verification and remaining limits

Executed with Kotlin 2.2.21 / JDK21 / JUnit4: **24 tests PASS** across
`MayaSpeechTest`, `MayaSpeechAlignmentTest`, `MayaSpeechTimingTest` and
`CloudSpeechTurnConstraintTest`. New tests cover exact text, supplementary
Unicode, original offsets, normalized/malformed timing rejection, duration
bounds, real-frame ordering, delayed UI dispatch and cancelled utterances.

The three production Maya files compile against Android API36. This is a
targeted compile, not a full Android build. Expanded mock HTTP/Robolectric
tests cover JSON/PCM decoding, model/voice identity, original alignment,
malformed-timing fallback and oversized/invalid responses; their Android
execution is pending the combined source-bound CI run. Activity/Avatar wiring
is in its companion commit and must be integrated before qualification.

Live with-timestamps transport, physical Fold7 sound/face synchronization and
device performance remain **NOT_RUN**. No paid generation, secret access,
signing, publication, installation or main merge was performed for this work.
Rollback is the scoped timing adapter plus companion Activity callback, keeping
the previously integrated Maya voice and network-veto fixes.
