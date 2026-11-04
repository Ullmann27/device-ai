# Installed TTS callback regression and repair

## Reproduced failure

The baseline local source `3198d0f7a52b438be7d4c3fe51de9e1e85db023c`
(published equivalent `c7053e50463e5d731c757a7706721e60eb77b1a8`, complete tree
`133d0a65fdfe6ed30bc013ee9971e1f81605a5cb`) added three cases to the existing
installed-listener fixture. The six-case run compiled successfully and finished
with four passes and two assertion failures, exit 1:

- A repeated real `onStart` changed the production PCM anchor from `(0, 219)`
  to `(0, 389)` instead of preserving it.
- A newer text range with a backward audio marker changed the anchor from
  `(100, 389)` to `(90, 559)` instead of rejecting it.

Raw XML, the actual Gradle request and the two assertion messages are retained
in `callback-baseline/`. The interrupted-stop test already passed at baseline;
it is retained as coverage of the existing production cleanup, not described
as a newly fixed interruption defect.

## Cause and bounded change

`AgentActivity.onStart` anchored PCM before asking the Director to accept the
start. Consequently even a duplicate rejected by the newer Director could
reset playback time. `onRangeStart` always anchored PCM and called the Director
without the available audio frame, leaving the ordering guard disconnected at
the real caller.

The existing #503 speech ordering, replay and elapsed-time smoothing changes
were selectively reused. The additional connection correction makes
`speechStarted` and `wordRange` return their synchronized acceptance decision.
The Activity anchors playback only after that decision and forwards the actual
range frame. There is still one Director and one existing speech signal.

A valid numeric range such as `42` advances accepted text/audio ordering and
can anchor playback without manufacturing a letter-derived mouth cue. Invalid,
replayed, backward and unrelated ranges do neither. The existing fallback
timestamp remains tied to actual emitted word cues. The reparenting fixture
now advances to the next distinct word instead of replaying the same range.

The fixture also invokes the installed listener's actual
`onStop(currentId, interrupted = true)`, observes cleared speech, starts a
successor and verifies that old stop/done/error events cannot terminate it.

## Executed intermediate verification

Local source `67ea15145510df5f41781ba019ea559df8491dbf`, published equivalent
`3eb39a7023d14afd9cadcc318a61a64d41f02b65`, complete tree
`6a3cbbe27d8ee649a274dba864798dc8146439bc` passed 33 cases in six classes:

| Class | Cases |
| --- | ---: |
| AvatarTtsLiveConnectionUiTest | 6 |
| AvatarDirectorTest | 4 |
| AvatarSpeechAcceptanceTest | 2 |
| AvatarSpeechBoundaryTest | 7 |
| AvatarSpeechClockTest | 4 |
| AvatarSpeechOrderingRegressionTest | 10 |

There were zero failures, errors or skipped cases; Gradle exited 0. The actual
compilation and test results are in `callback-connected/`. This intermediate
33-case result belongs to that source; the final combined source has its own
receipt and must not inherit this result without execution.

## Boundary

The test retrieves the Activity-created engine, initializes its real listener,
delivers synthetic accepted-request/PCM callbacks and lets the existing
scheduled `voiceFrame` reach the real visible view's reducer. The prior clock
fixture correction and clock assertions remain in place. Positive-path pose
updates are not injected directly into the Director or the view.

This is Robolectric LEGACY host graphics. It does not execute physical audio,
the full synthesis permission/acceptance decision, native Filament GPU pixels,
or a device session. The previous round's separate disconnected-PCM control
remains historical evidence; it is not relabeled as a new run in this round.
