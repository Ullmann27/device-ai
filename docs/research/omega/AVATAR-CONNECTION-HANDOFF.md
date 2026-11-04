# Avatar connection — verified continuation, 2026-11-04

## Outcome and exact source

Heinz authorized connecting the completed three-helper package. Current #491
already contains all three production fixes and all 26 associated regressions,
byte for byte. This continuation reconciles the helper branch with that input
and verifies the actual Activity-installed speech callback path. No duplicate
fanout, second avatar or additional production patch was needed.

| Identity | Exact commit/tree |
| --- | --- |
| Current #491 input | `e101e393dd6c80803ac9d9ac91fccd9812c4a18d` |
| Original #495 handoff | `6cf6e2128463e6a24dfac4652bb65e325ff6f18a` |
| Original tested helper production | `7859d5d212d61829fabc7dfdbd3ca7587852388d` |
| Published reconciliation | `5e2a0c2b26d1e2978b19d70878f30c98c36d94e9` |
| Published final test source | `32466a2559721762a9b6745890ea286d1c2730c5` |
| Actually executed local source | `17d9704cd3f5f3c774a7053b5aca03624ca6cde9` |
| Identical complete local/public tree | `5394ce13bf5c3b692f58aa284d9f8a89e6be882e` |
| Identical production app/src/main tree on #491 and tested source | `eaeda092326c11676543385eedd2ac86e0e4f194` |

The local/public commit identities differ because of commit metadata and local
diagnostic history. Their complete file trees match. Raw build logs identify the
actually executed local commit. The GitHub connector returned the matching tree
before the leased branch update. See
[source-publication-map.json](../../evidence/avatar-connection-20261104/source-publication-map.json).
The final handoff commit changes documentation/evidence only.

The original three production files and three regression files were independently
compared with the original tested helper source. Exact blobs and hashes are in
[source-identity.json](../../evidence/avatar-connection-20261104/source-identity.json).
The old 118-test result remains historical and source-bound; the current
136-test result was actually executed again.

## Connected production path

The Activity-created TextToSpeech engine installs the production progress
listener. Accepted utterance callbacks feed `TtsHologramSignal`; `onStart` posts
the existing scheduled `voiceFrame`. That frame sends the PCM-derived level to
the existing `AvatarDirector`, whose directly attached sink is the actual
visible `ContinuumHologramView`. Its `AvatarPoseReducer` supplies the rendering
pose. The optional native path uses `AvatarMorphMapper` before Filament
`setMorphWeights`.

Home and Chat retain the same view and director. The Home slot adapter docks
that surface only while it is in Home. Additional fanout would duplicate Home
delivery; replacing the direct sink with that adapter could lose Chat delivery
while the adapter is detached. The direct connection and existing stop, pause
and destruction cleanup paths are preserved. Source inspection is recorded in
[live-wiring-review.md](../../evidence/avatar-connection-20261104/live-wiring-review.md).

## New executable connection coverage

`app/src/uiTest/java/at/heinz/deviceai/AvatarTtsLiveConnectionUiTest.kt` adds three
Robolectric tests. They retrieve the Activity's actual TTS engine and invoke its
real initialization listener, installing the production progress listener.
Synthetic accepted-request state and PCM callbacks are then provided. No
positive-path Director or view event is injected as a shortcut.

1. **PCM, viseme and completion:** Before any word-range fallback, scheduled
   production frames must produce mouth opening and speech energy above 0.35
   in the visible view's reducer. A subsequent range callback must reach AA;
   completion must clear speech.
2. **Stale callbacks:** Start, synthesis, audio, range, completion, stop and error
   callbacks from an older utterance cannot rewrite the current pose. Finished
   callbacks cannot restart speech.
3. **Navigation/lifecycle:** The same visible surface continues receiving
   callbacks after reparenting into Chat. Pause clears speech; late callbacks
   and a deliberately invoked late frame cannot revive it.

The initial execution exposed a new fixture defect: Android virtual time advanced
171 ms while real JVM wall time advanced only 10–59 ms. Three new tests failed
while all 133 existing selected cases passed. Original failures and diagnostics
are preserved. The test-only fix instruments the existing Director and view for
Robolectric's time interceptor, reads `ShadowSystem` explicitly in the test, and
asserts clock equality before/after a controlled advance. No production clock,
Director instance, sink or original assertion was changed. The independent
[review](../../evidence/avatar-connection-20261104/tts-test-review.md) records the
initially missed fixture issue and its correction.

## Actual execution results

| Gate | Actual result | Evidence |
| --- | --- | --- |
| Fresh app/test Kotlin compilation | PASS, exit 0 | [Compilation log](../../evidence/avatar-connection-20261104/fresh-compilation/gradle.log) |
| Initial combined run | 136 tests: 133 passed, 3 new fixture failures | [Initial](../../evidence/avatar-connection-20261104/initial-callback-run/summary.json) |
| Clock diagnostic | 3 tests failed; two time domains measured | [Diagnostic](../../evidence/avatar-connection-20261104/clock-diagnostic-run/summary.json) |
| Corrected callback tests | 3 passed; no failures/errors/skips | [Targeted](../../evidence/avatar-connection-20261104/clock-fixed-targeted-run/summary.json) |
| Final combined run | **136 passed across 23 classes, 0 failures/errors/skips** | [Final](../../evidence/avatar-connection-20261104/final-connected-run/summary.json) |
| Configured Android lint | **PASS, 0 NewApi issues**, exit 0 | [Lint XML](../../evidence/avatar-connection-20261104/final-connected-run/lint-results-debug.xml) |
| Deliberately disconnected audio | Expected mouth-opening assertion failure; infrastructure succeeded | [Negative control](../../evidence/avatar-connection-20261104/disconnected-negative-control/summary.json) |

The final selection comprises the prior 118 component cases, 10 existing live
wiring cases, 5 adaptive navigation cases and 3 new TTS callback cases. This is
not every repository test. Exact class filters are in
[test-classes.json](../../evidence/avatar-connection-20261104/test-classes.json).
Each log records its full Gradle request, source identities and exit code.
The project configures lint with `checkOnly = NewApi`; this is not an all-rules
lint result. Existing compiler/deprecation warnings remain recorded in raw logs.

### Connection-sensitive negative control

An isolated unpublished worktree at
`206820cf88df7fe7ba798da63b2199c2d9bfd775` removed only
`avatarDirector.audioLevel(it, level)` from `voiceFrame`. The separate orb audio
envelope remained connected. Its test file was byte-identical to the positive
run. Compilation succeeded, then the PCM-only test failed at
`assertTrue(speaking.jawOpen > 0f)`, line 193: one AssertionError, no infrastructure
error. The test therefore detects the missing avatar audio connection before
any word-range approximation can substitute for PCM.

That worktree was restored in local commit
`e49fc4d96d2be7ae080391e9f97535acb6abe909`. The delivery source was never mutated.
The broken source was not published; the one-line patch, source identity and raw
failure are retained. No broad rerun was needed because the positive delivery
source remained unchanged.

## Existing asset and device boundaries

The packaged `continuum-avatar.glb` remains byte-identical to the original helper
input, SHA-256
`5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`.
Its existing Python validator still fails `rig_hierarchy_orientation`: jaw height
and eye-to-jaw spacing lie outside that older checker's fixed windows. Eight
validator self-tests pass; later validator gates are not reached. A historical
V2 receipt for a different asset is not transferred. The Kotlin asset contract
does not enforce those same windows, so this alone is not proof of an Android
load/render failure. See the
[asset check](../../evidence/avatar-connection-20261104/asset-check/README.md).

The verified boundary is the installed callback through scheduled frames to the
pose of the actual visible view under Robolectric LEGACY host graphics. The
fixture assumes an accepted synthesis request; it does not test the complete
`speakBootstrap` permission decision or audible TTS. Native GPU pixels, physical
Fold7 operation, audible output and reference-image acceptance remain NOT_RUN.
No APK, release, installation or visual acceptance is claimed.

## Integration and rollback

The open Draft [#495](https://github.com/Ullmann27/device-ai/pull/495) uses
`codex/continuum-three-avatar-helpers-20261104`, targeted at #491's
`codex/astra-jarvis-takeover-20261104`. A fresh GitHub read before the final
handoff confirmed #491 still at the exact input above. All three production
fixes are already present on that target independently. The additional callback
tests and evidence are on #495 for review.

EXP-07 independent verification, EXP-11 existing orchestration and EXP-12 exact
checkpoint/rollback remain compatible. No new state machine, dependency, model
execution, Android permission, worker or external AI service was introduced.
Other model/memory/bot work, avatar asset, signer and update channel are preserved.
No main merge or GitHub Actions execution took place.

Rollback for this continuation is omission/revert of the new callback test and
documentation. Do not reset another developer's branch or remove the original
fixes already on #491. Future source changes require checking the affected gates
again; these receipts remain bound to the recorded source tree.
