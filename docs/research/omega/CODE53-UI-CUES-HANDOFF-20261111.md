# Code53 existing UI cue assets

Base: `d006963764223a2c05f4cec21b5f0ddca0a27383`.
Coordinator target: PR #747 / `integration/continuum-final-visibility-20261111`.

The existing `ContinuumUiFeedback` helper can load the two bundled raw PCM
files below. No new event, service, audio focus request or network call is added.
Its microphone, pending speech, active speech, foreground, system sound, ringer,
music and rate-limit gates remain unchanged. Missing or invalid data uses the
existing synthetic cue. Each cue is read once, cached and released on close.

| Asset | Format | Maximum |
|---|---|---|
| `audio/ui/continuum-button.pcm` | PCM16 little-endian, mono, 16 kHz | 2,880 samples / 180 ms |
| `audio/ui/continuum-task-started.pcm` | PCM16 little-endian, mono, 16 kHz | 7,200 samples / 450 ms |

Both require zero endpoints, non-silence and peak amplitude at most 1,500/32,768.
The reader stops at one byte past the relevant budget and closes the stream on
success, read failure, corrupt data or missing data. A zero-length read fails
closed rather than spinning.

Executed: 6 pure decoder checks PASS; production helper compiles against API36.
The sixth check loads the actual generated assets through the production decoder
and pins their SHA-256, sample count and 1,200 peak. Button: 2,560 samples/160 ms,
SHA-256 `8d6dd464327d9a701b2411b03f629b76c451c012e448fc43238e41b1ab965d4e`.
Task start: 6,720 samples/420 ms,
SHA-256 `067ac28e4d29867197c066816f89f8b65ddce73c384d3634ceedfcd41f6860da`.
Both have silent endpoints. Root commits the assets and `provenance.json`
alongside this adapter; no duplicate asset copy is included here.

Android audio/policy UI tests and device listening acceptance remain pending.
The host decoder check proves content/format binding, not audible device quality.

Required hosted checks: `UiFeedbackPcmTest`, `ContinuumUiFeedbackTest` and existing
`ContinuumTaskEffectsCallerUiTest`. Full Node scan found no sonification-specific
source contract. The changed UI test retains duration, amplitude, silent-edge
and lifecycle assertions with the new explicit per-cue budgets.

Rollback: revert this scoped adapter or omit the assets to use synthetic cues.
Original avatar, package, model, internet consent and signing remain unchanged.
