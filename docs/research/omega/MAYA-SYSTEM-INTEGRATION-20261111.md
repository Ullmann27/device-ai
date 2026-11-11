# Maya system integration — Code53

Canonical target: `integration/continuum-final-visibility-20261111`, PR #747.
Starting remote source: `fd1de22cf3f6ed159601c9b944cc25c86f0fc221`.
This is the continuation of the existing Cinema/Maya candidate, not a new runtime.

## Verified original-signed Code53 draft — 2026-11-11

Original signing completed successfully at **2026-11-11 10:11:59 UTC**.
The completed run is https://github.com/Ullmann27/device-ai/actions/runs/43001264061,
with signer commit `9eb70e1d4737e7dfd1d30f65c3d1d6ab4f9f9d94`.
Preflight job `130457698399` and signing job `130457958455` both PASS.
The signed source is exactly `050a57cb019dcc1b7fe6cfa2fb5c11176b048e05`,
using the verified unsigned QA run `42985335421`.

- Verified unpublished draft: release ID `441524840`,
  https://github.com/Ullmann27/device-ai/releases/tag/untagged-6cb310026635b6d33f5f.
  Actual release metadata has `draft=true`, `prerelease=false`, `published_at=null`.
- Signed APK asset `735048782`: **102,717,942 bytes**, SHA-256
  `a4a806769ccdffcddc7c5e0b5762671983378c9edb8caa7f207430c7a235aa5f`.
- Original certificate SHA-256:
  `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.
  Package remains `at.heinz.deviceai.continuumlocal`, Code53 / `0.3.52`.
- Signed build-info asset `735048778`: 903 bytes, API SHA-256 digest
  `654ae6b97c9080426c0f8c2e8587f2e139347157d57b404130a6401c8c697c08`.

Completed signing logs confirm exact-source metadata/archives, original-key
certificate checks, signed APK certificate/package/hash, and draft/asset readback.
Final status is `SIGNED_DRAFT_VERIFIED_NOT_PUBLISHED`, with `PUBLISHED=false`.
The certificate checks use the pinned fingerprint; the fingerprint itself was
not printed literally in the signing log. Independent release API/body review
confirmed the source, certificate, identity, uploaded asset digests/sizes and
unpublished status. No new local signed-APK download is claimed. The build-info
asset digest is verified from API metadata, not a new local content download.

Published Latest remains Code51, release ID `438986340`. Code53 is signed and
available as the draft above; it has not been published or installed. Physical
Fold7, microphone, audible Maya, live lip/camera, Filament GPU and thermal
acceptance remain NOT_RUN. The unsigned input and its original hash below remain
part of the signing evidence, separate from the signed output.

## Verified unsigned QA input — 2026-11-11

- Runtime: `050a57cb019dcc1b7fe6cfa2fb5c11176b048e05`.
- Tree: `539ea3aac0af04644a015b3fb69ba865af80d1a8`.
- Workflow: `07efc77652638cfa40a67270475aa2c08ded5c76`.
- Run: https://github.com/Ullmann27/device-ai/actions/runs/42985335421
  / attempt `1` / job `130405169817`.
- New required preflight runs both Maya settings cases and preserves their XML
  before the unchanged full Android suite. The existing capture init forces the
  full test task to rerun; its minimum count and 29 required classes remain.
- Complete run, unsigned APK assembly and package validation PASS. Both
  uploaded ZIPs were downloaded, SHA-256/byte-verified and safely extracted.
- Final full suite: 804 XML files, 4,668 tests = 4,651 PASS /0 FAIL /0 ERROR
  /17 SKIP. All 29 required classes contain 168 PASS /0 FAIL /0 SKIP.
  The Maya settings preflight is separately 2/2 PASS, not added to 4,668.
- Critical: 136 PASS /0 FAIL /0 SKIP. Full Node: 2,458 PASS /0 FAIL /37 SKIP.
  Both reports have `sourceUnchanged=true` and the exact `050a57cb` commit.
  Native ICU: 22 patterns PASS. The configured **NewApi-only** lint XML has
  zero issues; this is not a claim about a broader lint scope.
- Unsigned QA APK: 102,701,335 bytes; SHA-256
  `28153df1b4e6f0d97a8f625a7f444248efc6ee09436e7e77c5aab4e3aaaa9c0e`.
  It matches `SHA256SUMS.txt` and the validated report's hash and byte count.
  Identity: `at.heinz.deviceai.continuumlocal`, versionCode 53,
  versionName `0.3.52`, ARM64, minSdk 26, targetSdk 36, not debuggable.
- APK artifact `13842542249`: 73,412,550 ZIP bytes, SHA-256
  `083c2df520fb5356d8d6357cbb0d7eb57b6aa279fd57db7ec2c0ac2d9a1573ee`.
  QA artifact `13842736860`: 426,597,327 ZIP bytes, SHA-256
  `a08c090d94ec63edf7ca08b36fda00ff286fa7eb9b0e42b6ad226e679098708e`.
- Actual report path inside the QA artifact:
  `_temp/code53-package/RESULT.json`; report SHA-256
  `fb9a78ec26af6dec2f4b983cb62cbf3691785d1f1763f6f615e6f917952cf496`.
  Validator SHA-256:
  `2a0aeaef48bb5482f0da304ee24e3a69a66d3fa2a86c81885436d28b7c8a535f`.
  Source commit/tree are identical and clean before/after validation.
  Both `BUILD-INFO.txt` copies bind the exact source and workflow above.

Portable complete evidence, including the 29 class totals and artifact-relative
report paths: `docs/evidence/final-visibility-20261111/code53-050a57cb-verified.json`.

Only the settings test and technical evidence changed after the failed app
source. The production runtime remains unchanged. The completed signer binding
uses the verified identities above; the failed `fd86f11a` run is superseded.
Later documentation commits do not change this completed run's fixed source or
workflow identity. Original signing has completed as recorded above. The
unsigned QA input is retained unchanged; physical-device acceptance remains separate.

The superseded run `42984950999` was cancelled before an Android result.
An isolated Gradle 8.11.1 reproduction showed that the CLI `--tests` argument
enters `startParameter.taskNames` and violates the existing strict QA allowlist.
The corrected workflow applies the same exact class filter through a temporary
init script; the task allowlist is unchanged. The subsequent full invocation
does not load that filter and still reruns all tests.

## Superseded failed frozen QA — 2026-11-11 08:04 UTC

- App source: `fd86f11a4a822ee4805f4c462791baeb1d741db4`.
- App tree: `21e5603d2a3f20c840bc3c2ba7836be408dbb5ad`.
- Workflow: `23146e7f817b825e48a2b086f73da5cf28e0a274`.
- Failed run: https://github.com/Ullmann27/device-ai/actions/runs/42977884118
  / job `130381258271`.
- Source/ICU/critical/full-Node, backend/software rendering and SDK steps PASS.
  Android production/test compilation passed. Full Robolectric reports
  4,650 PASS /1 FAIL /17 SKIP (4,668 total). The only failure is
  `MayaVoiceSettingsUiTest.mayaSetupIsReachableWithoutAndroidTtsAndNeverAutoGeneratesASample`,
  at `assertNotNull(input.error)`. All 29 required classes are present:
  167 PASS /1 FAIL /0 SKIP; 28 classes are fully green. Configured NewApi lint
  has no confirmed result; APK build and package validation were skipped.
  Evidence: `docs/evidence/final-visibility-20261111/code53-fd86-hosted-test-summary.json`.

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

## Original-signing history and completed draft

PR https://github.com/Ullmann27/device-ai/pull/752 records the original signer
base `0199741efba0a90e20684520762c7d8ded0fcfc8` on
`chatgpt/code52-owner-signing-20261109`. Initial remote checkpoint:
`cf5658b056d21ba0c0ac5b0ec6d60420ef6f2f01`, tree
`e9f376edba48b72b49c3f3c7d7c6d9c6f6dff51b`.

It reuses the registered Code52 workflow and shared signer/draft body, with an
exactly separate Code53 evidence profile. Independent review found no remaining
concrete defect in this WIP production delta. Current published Latest was checked
as `continuum-local-code51-6c23d295e2fca2d4e5950a866547733ec5ff22b0`, so its strict
existing baseline gate remains valid. At that historical checkpoint the protected
signer branch was unchanged.

Superseded prep checkpoint: `77e23d3ee58d8e0c8795bca01dd6fe80e20678c1`,
tree `6976321e709ed21fbe966befcc59baa5818a69a0`. It binds the active corrected
050a57cb/07efc776 run above and rejects both superseded attempts. Independent
review confirmed the exact pins and required Maya preflight.

At that earlier checkpoint the production binding intentionally remained absent
and its sixteenth data/archive/workflow case was red. That incomplete state is
superseded by the real artifact binding below; it was never a passing signer result.

Final binding checkpoint: `25a2f53c9e15ac45df487a53414c154ce80b6631`, tree
`420888da86f10fe54719d1a7781fc03614d48fc2`, on
`prep/code53-original-signer-20261111`. The unchanged production verifier accepted
the actual downloaded APK/QA ZIPs and complete GitHub metadata. Hosted workflow
`continuum-code52-owner-signing-check.yml`, run
https://github.com/Ullmann27/device-ai/actions/runs/42994732638, attempt 1,
job `130435861017`, checked exactly that commit: **20/20 PASS**
(4 Node contracts and 16 Python contracts, no failures or skips).

The owner has explicitly authorized original signing of this built Code53 APK.
The narrow shared-signer draft-release-ID HTTP 404 correction is now published
on the prep branch at `9eb70e1d4737e7dfd1d30f65c3d1d6ab4f9f9d94`, tree
`9d82191671827d9593bb3b5492b55f5926f49344`. Hosted run
https://github.com/Ullmann27/device-ai/actions/runs/42997317251, attempt 1,
job `130444533336`, checked exactly `9eb70e1d`: **28/28 PASS**
(12 Node contracts and 16 Python contracts, zero failures/skips/cancellations).
The earlier 20/20 result remains bound to `25a2f53c`. GitHub now reports
PR #752 merged into the existing signer branch; the app main branch is unchanged.
The existing signer branch `chatgpt/code52-owner-signing-20261109` is now
activated at exactly `9eb70e1d`: GitHub `update_ref` succeeded with
`force:false` and expected previous commit
`0199741efba0a90e20684520762c7d8ded0fcfc8`. A separate GET of the branch ref
confirmed the exact activated commit. Its automatic protected-branch contract
run https://github.com/Ullmann27/device-ai/actions/runs/42997592914 also
completed successfully on this same commit.

The authorized manual signing workflow completed successfully as run
`43001264061`, producing the verified original-signed unpublished draft recorded
above. No key is exported. Published Latest remains the Code51 tag above;
Code53 publication and installation have not occurred. Physical Fold7/audio/GPU/
thermal acceptance remains NOT_RUN.

The existing workflow is
https://github.com/Ullmann27/device-ai/actions/workflows/continuum-code52-sign-release.yml.
The completed dispatch used `chatgpt/code52-owner-signing-20261109` and these exact inputs:

| Input | Value |
| --- | --- |
| `source_sha` | `050a57cb019dcc1b7fe6cfa2fb5c11176b048e05` |
| `qa_run_id` | `42985335421` |
| `owner_approved_sha` | `050a57cb019dcc1b7fe6cfa2fb5c11176b048e05` |

The existing `continuum-original-signing` protection remained in place. The
actual signed-draft receipt above records the completed run, real draft URL,
original certificate, signed APK SHA-256/bytes and uploaded build-info metadata.
The historical unsigned QA artifact is preserved as the input receipt.

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

The completed `42977884118` Android run found one queued-dialog test failure.
The settings test now explicitly uses Robolectric's paused looper and delivers
`onShow` before clicking, and `onDismiss` before asserting credential clearing.
No production dialog behavior or existing assertion was removed. Invalid-key
checks additionally require Maya to remain disabled and its credential absent.
The corrected workflow runs both settings tests first, preserving their XML
separately, then reruns the unchanged full suite. Both focused cases passed in
run `42985335421`, followed by the complete Android/APK PASS recorded above.
The earlier queued-dialog failure is superseded.

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
  full Robolectric execution was then a combined CI gate, now passed above.
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

These were component/host results before the combined gate. The completed frozen
workflow now also compiled all Android sources, passed its full suite and required
Maya/gaze regressions, passed configured NewApi lint, and validated the unsigned
ARM64 APK's package/version/assets. Workflow and tested-source commits remain
explicitly separate; a hosted PASS is not physical-device acceptance.

## Current-source visual review

The verified current QA artifact contains 596 PNGs, including 67 explicitly
declared native-Canvas host images. Forty-three have sidecars with the exact
`050a57cb` source; all 22 `native-*.json` declared PNG hashes match their images.
The remaining native sequence includes 24 frames with its own host-capture
manifest at `_temp/code53-ui/fullstage-native-activity/capture.json`.

The visual reviewer opened exactly eight current original PNGs: cover and inner
`work-2100ms`, `returned`, `aborted-return`, and system `diagnosis`. STOP/close
controls are readable and unobstructed; the avatar is present; completion and
abort return to the cockpit without a retained work window. System diagnosis is
readable and visibly marked as synthetic data. No new concrete deviation was
found in these eight images compared with the previous diagnosis.

The 200% font limitation below was observed in the superseded `fd86f11a` artifact.
The current 200% frames were **not reopened**. Production code is unchanged
between `fd86f11a` and `050a57cb`; retain the limitation, but do not represent it as
a new current-source 200% visual inspection. These captures use Robolectric API28
native Canvas and the existing 2D avatar fallback, not physical Fold7 or Filament
GPU rendering. Audible lip timing remains NOT_RUN.

## Prior-run visual diagnosis

During the corrected suite, artifact `13839285568` from completed run
`42977884118` was downloaded and verified locally: 426,536,310 bytes, ZIP SHA-256
`93276da1c35fb9e9e211efb9efbfb61a4e409fce73ae49256378350babbc58ea`.
All 2,452 archive names were safe and unique; 67 original PNGs and selected
metadata were extracted. Twenty-two PNG hashes matched their individual
manifests and `fd86f11a` source. The earlier log-summary JSON records its original
not-downloaded state; this is the later separate archive verification.

The visual reviewer inspected 29 original frames; the integrator independently
opened eight. Standard phone/fold working windows, STOP/close controls and
return/abort frames show no additional demonstrated operating blocker. Scripted
mouth states and full-stage memory/orb transition frames remain visible. The
`result-900ms` frame intentionally precedes task completion in the capture test;
the later returned frame restores the cockpit.

Known readability limit: at 200% font scale, the decorative Continuum title is
clipped at the right and compact tile labels are shortened. The scrolled actions
view retains navigation. Do not describe this as flawless enlarged-text layout.
The capture manifest explicitly identifies Robolectric API28 native Canvas and
the existing 2D fallback; Filament, physical Fold7 and audible lip timing remain
NOT_RUN. This old-run review is diagnostic, not acceptance of the active source.

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
The exact-source APK/QA evidence and original signing are complete. The shared
signer correction passed its exact hosted contract check, and the activated
signer produced the verified Code53 draft in run `43001264061`. This checkpoint
contains the signing receipt; it does not record an app-main merge, Code53
publication or installation. Published Latest remains Code51. Publication and
physical-device acceptance remain separate owner gates.

Rollback: revert the scoped fixes together with their regressions; Maya can
also be disabled through the existing voice picker. Preserve donor worktrees
and previously published branches. Main integrator owns remote publication and
the frozen QA result; component handoffs describe their narrower evidence.
