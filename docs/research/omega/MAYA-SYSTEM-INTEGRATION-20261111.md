# Maya system integration — Code53

Canonical target: `integration/continuum-final-visibility-20261111`, PR #747.
Starting remote source: `fd1de22cf3f6ed159601c9b944cc25c86f0fc221`.
This is the continuation of the existing Cinema/Maya candidate, not a new runtime.

## Current build blocker

Run https://github.com/Ullmann27/device-ai/actions/runs/42972659558
validated source `d94b9b58dba5520cc4bffb02bf9a5cfc8bceb89e` through Node,
backend and software-rendering checks. Android compilation failed at
`JarvisWorkWindowHost.kt:177`: missing `orb` constructor argument. No APK was
produced. The result-window plan now preserves the original orb geometry.

## Coordinated repairs

- Voice: explicit offline/Internet/cloud transport vetoes also constrain Maya.
  Research-only restrictions remain separate from optional speech transport.
- Conversation: owner speech interruption updates the existing hands-free state
  even when Maya correctly suppresses its cancelled callbacks. Asynchronous
  replies retain their original request's speech permission.
- Avatar: foreground, detach and motion transitions use the same monotonic
  clock as camera observations; stale samples remain rejected.
- Build: the original window-to-result path carries its existing orb bounds.

The common path stays ASR -> owner input -> existing context/model runtime ->
approved reply queue -> Maya or local Android speech -> existing utterance
callbacks -> original avatar. No extra model, memory store, renderer or bus.

## Executed checks before combined QA

- Maya/constraint host tests: 16 PASS after the language-boundary review;
  the final regression set reproduced two earlier negation/word-order errors.
- Conversation host tests: baseline 1 FAIL (SPEAKING instead of PAUSED), then
  21 PASS; 15 existing voice/avatar wiring checks and 14 context/streaming
  checks PASS.
- Gaze source-bound replay: baseline 3 FAIL, corrected 3 PASS; 76 neighboring
  avatar/reducer/gaze host checks PASS.
- Original avatar GLB unchanged; SHA-256
  `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.

These are component/host results, not a green combined Android build. The
frozen-source workflow must compile all Android sources, run its full suite and
required Maya/gaze regressions, run configured lint, and validate the unsigned
ARM64 APK's package/version/assets. Workflow commit and tested source commit
remain explicitly separate.

## ElevenLabs and device boundary

The connected ElevenLabs account confirmed the selected public voice
`ii0s2u4R3UFnxKL6DOrz`, Maya - Warm Californian. The implementation retains
`eleven_multilingual_v2` and PCM24k. A short synthetic German connector probe
started two takes, reported at 446 credits total. This is a connector generation,
not Android playback or a subjective pronunciation acceptance.

The Android app uses its existing separate encrypted local ElevenLabs key
setup. A ChatGPT connector connection does not install a credential on the
phone. Global OFF/ASK and per-turn offline constraints retain local speech.
No credential is included in project source, evidence or the APK.

Maya currently buffers each bounded reply chunk. PCM amplitude reaches the
avatar; exact word/phoneme alignment is not implemented. Fold7 microphone,
audible Maya, live mouth/camera behavior, thermal behavior and installation
remain NOT_RUN. The selected voice and standard-German preference are retained.

## Compatibility and next gate

EXP-05/12/14 boundaries remain; research flags/weights are unchanged. Preserve
package `at.heinz.deviceai.continuumlocal`, Code53 and the original signer.
No main merge, signing, publication or installation is performed by these
repairs. Prepare exact-source evidence before any owner signing decision.

Rollback: revert the scoped fixes together with their regressions; Maya can
also be disabled through the existing voice picker. Preserve donor worktrees
and previously published branches. Main integrator owns remote publication and
the frozen QA result; component handoffs describe their narrower evidence.
