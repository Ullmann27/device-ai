# Maya system integration — Code53

Canonical target: `integration/continuum-final-visibility-20261111`, PR #747.
Starting remote source: `fd1de22cf3f6ed159601c9b944cc25c86f0fc221`.
This is the continuation of the existing Cinema/Maya candidate, not a new runtime.

## Active frozen QA — continuation checkpoint, 2026-11-11 07:45 UTC

- App source: `fd86f11a4a822ee4805f4c462791baeb1d741db4`.
- App tree: `21e5603d2a3f20c840bc3c2ba7836be408dbb5ad`.
- Workflow: `23146e7f817b825e48a2b086f73da5cf28e0a274`.
- Active run: https://github.com/Ullmann27/device-ai/actions/runs/42977884118
  / job `130381258271`.
- Source/ICU/critical/full-Node, backend/software rendering and SDK steps PASS.
  Full Android/Robolectric/configured NewApi lint is IN_PROGRESS. APK, package
  validation and complete-run success remain PENDING, not passed.

Later documentation commits do not change these frozen runtime/workflow identities.
Read the actual run before starting any replacement. A previous successful full
Android step took 48m44s (run `42934541308`); the more recent failing baseline
completed 4,619 cases in 43m20s (run `42966389910`). These are comparison timings,
not a completion promise. Running job logs returned BlobNotFound before completion.

Run `42977222214` was superseded and cancelled after its completed Node gates.
Its exact-source reports record 136 critical PASS, 2458 full-Node PASS /37 SKIP
/0 FAIL, with sourceUnchanged=true. They do not supply an Android result.
The replacement corrects a historical harness binding: the same SHA-256-checked
APK validator is imported with isolated Python and receives only the fixed
current source/tree constants. Original avatar, package, ABI, unsigned, all asset
hashes and clean-source checks remain active. Wrong SHA, wrong tree and dirty
source still reject; the original fixed a980 comparison was reproduced as rejecting.

## Prepared original-signing continuation

Draft https://github.com/Ullmann27/device-ai/pull/752 preserves the actual signer
base `0199741efba0a90e20684520762c7d8ded0fcfc8` on
`chatgpt/code52-owner-signing-20261109`. Initial remote checkpoint:
`cf5658b056d21ba0c0ac5b0ec6d60420ef6f2f01`, tree
`e9f376edba48b72b49c3f3c7d7c6d9c6f6dff51b`.

It reuses the registered Code52 workflow and shared signer/draft body, with an
exactly separate Code53 evidence profile. Independent review found no remaining
concrete defect in this WIP production delta. Current published Latest was checked
as `continuum-local-code51-6c23d295e2fca2d4e5950a866547733ec5ff22b0`, so its strict
existing baseline gate remains valid. The protected signer branch is unchanged.

Code53 remains NOT_READY_FOR_SIGNING: its production binding file intentionally
does not exist until the actual complete run and artifacts are verified. Four
legacy contracts and twelve new data/archive/workflow cases pass; the thirteenth
production-binding case is still red. This is not a total passing signer result.

Next: finish the active Android/APK run; verify both artifact API IDs/digests and
downloaded ZIP bytes, actual APK SHA/size, exact validator-report path, source/tree,
workflow receipt, JUnit totals and all 29 required classes. Then fill the real
Code53 binding, run all signer contracts and its secrets-free check on the one
prep branch. Exact-source owner approval and the existing protected environment
remain the final signing gates. No main merge, signer activation or installation
has been performed. Keep physical Fold7/audio/GPU/thermal acceptance NOT_RUN.

## Build failures and repairs

Run https://github.com/Ullmann27/device-ai/actions/runs/42972659558
validated source `d94b9b58dba5520cc4bffb02bf9a5cfc8bceb89e` through Node,
backend and software-rendering checks. Android compilation failed at
`JarvisWorkWindowHost.kt:177`: missing `orb` constructor argument. No APK was
produced. The result-window plan now preserves the original orb geometry.

The subsequent run https://github.com/Ullmann27/device-ai/actions/runs/42975411633
tested source `f99082ea0d98d74eb52b28f01b74629676572081`. Critical Node and
the targeted Kotlin gate passed. Full Node stopped on two old exact
`addAssistantMessage` signatures after the captured speech permission was added;
Android and APK stages did not run. Local replay: 2456 PASS, 2 FAIL, 37 SKIP,
source unchanged. The two tests now accept the additional parameter while
preserving all cockpit/navigation assertions: 7/7 focused checks PASS.

## Coordinated repairs

- Voice: explicit offline/Internet/cloud transport vetoes also constrain Maya.
  Research-only restrictions remain separate from optional speech transport.
- Conversation: owner speech interruption updates the existing hands-free state
  even when Maya correctly suppresses its cancelled callbacks. Asynchronous
  replies retain their original request's speech permission.
- Avatar: foreground, detach and motion transitions use the same monotonic
  clock as camera observations; stale samples remain rejected.
- Build: the original window-to-result path carries its existing orb bounds.
- Speech timing: the existing Maya provider requests bounded PCM24k plus
  original-text word alignment. AudioTrack playback positions drive range
  callbacks into the existing avatar listener. Missing or invalid alignment
  keeps PCM playback; stale/cancelled callbacks cannot animate another utterance.
- UI sound: two bounded ElevenLabs-generated PCM16 assets replace the existing
  button/task-start cue samples. Existing microphone, speech, foreground,
  system-sound and ringer gates still control playback. Missing/invalid assets
  use the previous synthesized cues.

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
- Provider timing/alignment component suite: 24 host tests PASS. Android API36
  compilation PASS for production provider classes and HTTP/UI test bodies;
  full Robolectric execution remains a combined CI gate.
- Activity/avatar timing chain: 21 composed JVM checks and 10 Node wiring
  checks PASS. Existing utterance/foreground checks and German viseme
  approximation remain in use.
- Combined timing/context snapshot `cd5e3adbf626fb3b00aaa710953bfe2dd3d94131`:
  23 source files compiled together, 48/48 JVM and 22/22 focused Node checks
  PASS. Actual bundled cue decoding: 6/6 host checks PASS with pinned hashes.
- Android runtime review found an unsupported embedded `U` regex flag in the
  new transport-veto rules. The narrow correction retains Unicode umlaut
  boundaries explicitly. Native ICU rejects the old negative control and
  compiles all 22 current policy expressions; 25 Maya/alignment/policy JVM
  checks PASS. The frozen workflow also runs this real ICU compatibility gate.

These are component/host results, not a green combined Android build. The
frozen-source workflow must compile all Android sources, run its full suite and
required Maya/gaze regressions, run configured lint, and validate the unsigned
ARM64 APK's package/version/assets. Workflow commit and tested source commit
remain explicitly separate.

## ElevenLabs and device boundary

The connected ElevenLabs account confirmed the selected public voice
`ii0s2u4R3UFnxKL6DOrz`, Maya - Warm Californian. The implementation retains
`eleven_multilingual_v2` and PCM24k. A short synthetic German connector probe
completed two MP3 takes (14.63 s and 14.02 s), reported at 446 credits total.
One existing take was transcribed once with the connected `eleven_scribe_v1`
tool: all 34 words and the full 223-character text matched exactly. The input
was read back from the original flow node, rather than its truncated preview.
Evidence: `docs/evidence/final-visibility-20261111/maya-german-roundtrip.json`.
This verifies the short connector probe's intelligibility; it does not verify
Android playback or subjective naturalness. The app's existing ASR is unchanged.

The Android app uses its existing separate encrypted local ElevenLabs key
setup. A ChatGPT connector connection does not install a credential on the
phone. Global OFF/ASK and per-turn offline constraints retain local speech.
No credential is included in project source, evidence or the APK.

Maya buffers each bounded reply chunk. PCM amplitude and validated provider
word timing reach the original avatar. Mouth shapes remain the existing
German-text approximation; provider phoneme-perfect lip synchronization is
not claimed. Android on-device ASR remains the established microphone path;
no separate cloud recorder, transcription or model runtime is introduced.
Fold7 microphone,
audible Maya, live mouth/camera behavior, thermal behavior and installation
remain NOT_RUN. The selected voice and standard-German preference are retained.

The two bundled UI cues were selected from eight completed ElevenLabs
`eleven_text_to_sound_v2` variants. The provider reported 13.3333 credits total
for those variants. The selected files are mono PCM16/16 kHz, 160 ms and
420 ms, peak amplitude 1200, with silent endpoints. Their measured bounds,
generation identifiers, normalization recipe and SHA-256 values are recorded
in `app/src/main/assets/audio/ui/provenance.json`. No provider credential or
temporary download URL is bundled. This is measured asset validation, not a
subjective audible or Fold7 acceptance.

## Compatibility and next gate

EXP-05/12/14 boundaries remain; research flags/weights are unchanged. Preserve
package `at.heinz.deviceai.continuumlocal`, Code53 and the original signer.
No main merge, signing, publication or installation is performed by these
repairs. Prepare exact-source evidence before any owner signing decision.

Rollback: revert the scoped fixes together with their regressions; Maya can
also be disabled through the existing voice picker. Preserve donor worktrees
and previously published branches. Main integrator owns remote publication and
the frozen QA result; component handoffs describe their narrower evidence.
