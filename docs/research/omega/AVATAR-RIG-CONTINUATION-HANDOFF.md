# Avatar rig, motion and speech continuation — 2026-11-04

## Outcome and exact source

This owner-authorized round used exactly three development helpers. It connects
existing #502/#503 motion and speech work, repairs the actual TTS callback/PCM
acceptance boundary, fixes a reproduced signed-zero crash in idle motion, and
verifies the current packaged rig with the existing separate V2 validator.

The final selected unit-test execution has **168 passing cases in 28 classes,
zero failures/errors/skips**. The original V1 asset failure is preserved alongside
the freshly passing separate V2 result. The V2 adversarial suite has **16 passing
tests**. These are bounded host/numerical results, not device or native-pixel
acceptance.

| Identity | Exact value |
| --- | --- |
| Existing target #491 | `e101e393dd6c80803ac9d9ac91fccd9812c4a18d` |
| Prior published #495 checkpoint | `0e2b9ea59e71255e36580d695b16220d1d2974c6` |
| Pinned #502 motion input | `6cb9e760f0a16a6f34a8442abf9b79dcf2705799` |
| Pinned #503 selective source | `2582be86c121d7f6d1179c7edc49e979bee8ba9c` |
| Final actually executed local source | `f5dc6e08acb88b30273410b8efcc7e0db446020b` |
| Published equivalent source | `df3caae21c1729de2b73a688241d3d53ef3783d4` |
| Identical complete source tree | `58dc99f0145272775e0a2554d7d8a4ccc7d6be4a` |
| Final production app/src/main tree | `9b4749059448e194819f8f089cf18890757bf941` |

Local and public commit IDs differ because publication uses the GitHub connector
with existing remote parents. The complete file trees were compared before each
leased, non-forced ref update. Raw execution logs name the commit that actually
ran. The final evidence/handoff commit adds documentation only.

The previous 136-case result remains historical in
[AVATAR-CONNECTION-HANDOFF.md](AVATAR-CONNECTION-HANDOFF.md). It was not transferred
to this new source combination. All source/publication mappings and changed
app-file hashes are retained in the new evidence directory.

## Three completed helper contributions

| Helper | Actual contribution |
| --- | --- |
| avatar_rig_contract | Reproduced V1 failure; located the exact original f39 asset; ran the existing V2 against current 5f49; measured the hinge and neutral transforms. No model or checker patch was necessary. |
| avatar_render_motion | Reviewed and selectively integrated existing mapper/idle code, reproduced and fixed signed zero, retained general jaw-transition coverage, added idle regressions, investigated the one obsolete cross-mapper test, and inspected the local lint execution configuration. |
| avatar_independent_review | Independently reviewed the callback acceptance change and all six installed-listener cases; found no blocking static issues; independently retained the actual V1 failure and eight passing checker self-tests. |

The coordinator handled real caller integration, source compatibility, serialized
Gradle runs, the integrated V2 adversarial run and publication. Helpers worked in
isolated worktrees. These were development helpers for this round; no live
Continuum app worker activation is claimed. This addresses the continuing
G1-R2-AVATAR-VERIFY and #510 speech/interruption review points through the existing
avatar path.

## Production behavior

### Actual installed TTS callback boundary

The Activity previously anchored PCM before the Director could reject a replay,
and its range callback did not forward the available audio frame. Two new cases
through the actually installed listener reproduced anchor rewinds before repair.

The Director now returns its synchronized acceptance decision from
`speechStarted` and `wordRange`. The Activity anchors playback only after an
accepted decision and passes the actual range frame. Duplicate starts and
backward markers no longer reset the PCM position through those callbacks.
Valid numeric ranges advance accepted ordering and playback anchoring without
inventing letter-derived visemes. No second ordering state machine was added.

The existing #503 replay guards, terminal-ID retention and elapsed-time audio
smoothing were reused. Actual interrupted-stop callbacks clear speech, and late
callbacks from that utterance cannot terminate its successor; this cleanup already
passed the baseline and is retained as coverage, not portrayed as a newly fixed
interruption bug. The reparent test advances to the next valid word while keeping
its original PCM/AA assertions.

The independent review found no blocking static issue and explicitly distinguishes
a synchronized Director decision from a global transaction spanning the separate
Director and PCM monitors. The actual Activity caller executes acceptance and
anchoring on its UI-thread path.

### Mimik and cosmetic movement

The existing #502/#503 mapper supplies bounded idle head/eye movement, held gaze
fixations, short transitions and occasional double blinks. Observed gaze and
non-idle runtime states retain priority; reduced motion stops cosmetic movement.
Repeated rendering at one time does not advance an active vowel transition.

Active PP, representing M/B/P closure, immediately retires outgoing opening
morphs and suppresses the rendered jaw opening. General AA-to-SS opening and
inner-jaw alignment tests remain alongside the new PP closure tests.

The unmodified upstream idle class threw `ArrayIndexOutOfBoundsException` for
`-0.0f`, `-460f` and `-920f`: binary search placed negative zero before the
first positive-zero boundary. Normalizing an exact zero remainder fixes the
index without altering the motion schedule. The unchanged JVM probe had three
case errors before correction and none across its nine inputs afterward.
The probe catches exceptions per case, so its process exit 0 is not mistaken
for a successful original run.

One combined-run failure exposed an old test contract: it expected PP JawOpen=1
and reused a mapper across different visemes at the same timestamp. The corrected
test keeps all 15 visemes, compares all 21 weights and inner-jaw angles, uses
independent static samples, and explicitly requires PP closure. A new real
LipSyncFaceSource-to-pose-to-mapper test preserves measured energy and raw jaw
while checking final closure at an unchanged render time. No source/bridge
production wiring was invented: the visible path remains View → Filament →
AvatarMorphMapper. The alternate faceWeights/pose bridge has no current
production caller in the inspected source.

The old standalone Fable Main.kt runner was not imported because two of its
expectations contradict the newer PP and repeated-time behavior. Its claimed
host-test total is not used as evidence for this delivery.

## Executed results and retained failures

| Gate | Actual result | Receipt |
| --- | --- | --- |
| New installed-listener baseline | 6 cases: 4 pass, 2 anchor-rewind failures; exit 1 | [Baseline](../../evidence/avatar-rig-continuation-20261104/callback-baseline/summary.json) |
| Connected speech subset | 33 cases in 6 classes, zero failures/errors/skips; exit 0 | [Connected](../../evidence/avatar-rig-continuation-20261104/callback-connected/summary.json) |
| First motion/speech combination | 167 cases: 166 pass, 1 obsolete PP comparison fails; exit 1 | [Initial combination](../../evidence/avatar-rig-continuation-20261104/combined-initial/summary.json) |
| Final selected test execution | **168 cases in 28 classes, zero failures/errors/skips** | [Final tests](../../evidence/avatar-rig-continuation-20261104/combined-final/summary.json) |
| Configured Android API analysis | **0 NewApi issues, exit 0**, separate bounded-JVM run | [Final lint](../../evidence/avatar-rig-continuation-20261104/lint-final/summary.json) |
| Original/fixed signed-zero probe | 3 recorded case exceptions before; 9 OK inputs and 0 case exceptions after | [Probe](../../evidence/avatar-rig-continuation-20261104/signed-zero/probe-manifest.json) |
| Existing V1 checker self-tests | 8 pass in the independent helper's unchanged-input run | [Raw self-test output](../../evidence/avatar-rig-continuation-20261104/independent-review/checker-selftests.stderr.txt) |
| Current asset under separate V2 | PASS; actual V1 FAIL retained separately | [V2](../../evidence/avatar-rig-continuation-20261104/hinge-v2-final/candidate.json) |
| Existing V2 adversarial self-tests | **16 pass**, exit 0 | [Raw self-test output](../../evidence/avatar-rig-continuation-20261104/hinge-v2-final/adversarial-selftests.stderr.txt) |

The 28 class filters are explicit in
[test-classes.json](../../evidence/avatar-rig-continuation-20261104/test-classes.json).
This is 32 more selected cases than the prior 136-case checkpoint, not a claim
that every repository test ran. App and test Kotlin compilation succeeded;
existing deprecation warnings remain in the raw logs.

The final combined command finished all 168 test cases and advanced into lint,
then its Gradle daemon disappeared. Its overall process exit is therefore **1**,
despite the passing XML suites. A separate 1536 MiB lint attempt also lost the
daemon. Both failures are retained; neither is relabeled as a successful build.

After local inspection confirmed that AGP runs lint inside the Gradle JVM, a
third lint execution used a 1024 MiB heap, 384 MiB metaspace, 128 MiB code cache,
two active processors, explicit OOM exit and a GC log. It completed successfully
on the same unchanged source. All three lint analysis tasks executed. The report
task was UP-TO-DATE and reused the unchanged zero-issue reports after that
successful analysis; report timestamps are not represented as fresh generation.
No rule was disabled. The project configures only NewApi, so this is not an
all-rules lint result. The two earlier daemon failures have no proven cause;
the successful bounded run does not retrospectively prove an OOM diagnosis.

Detailed callback reasoning, the exact reviewed diff and static review are in
[callback-repair.md](../../evidence/avatar-rig-continuation-20261104/callback-repair.md),
[callback-acceptance.patch](../../evidence/avatar-rig-continuation-20261104/callback-acceptance.patch)
and [tts-bridge-review.md](../../evidence/avatar-rig-continuation-20261104/independent-review/tts-bridge-review.md).
The one-file test reconciliation and live-caller analysis are in
[lipsync-contract/review-note.md](../../evidence/avatar-rig-continuation-20261104/lipsync-contract/review-note.md).

## Current GLB: V1 remains separate from the passing V2

The packaged GLB is unchanged, SHA-256
`5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`.
The existing original file has the required
`f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e` pin.

Actual V1 still fails its fixed jaw-height/eye-gap windows and exits early.
Existing V2 checks the functional hinge before applying unchanged V1 to an
explicit, neutral-preserving diagnostic coordinate view. The actual hinge at
Y=0.2669999981 m lies above oral support at Y=0.2597861290 m and below the eyes at
Y=0.2884144187 m. The maximum neutral control/child matrix deviation is about
2.78e-17. Actual tooth geometry and hierarchy are checked separately.

Both the helper and coordinator obtained
`PASS_SEPARATE_HINGE_AND_FIXED_REGION_V2` on this current asset. The coordinator
also ran the existing 16 adversarial cases. The exact asset/checker/self-test
inputs are byte-identical between that run and final source. No bounds were
relaxed, no GLB was replaced, and no historical receipt for a different asset
was transferred. The actual V1 FAIL remains in the same record as the V2 PASS.

See [rig-validation.md](../../evidence/avatar-rig-continuation-20261104/rig-validation.md)
for pins, measurements, commands and the limits of that conclusion.

## Verification boundary and handoff

The installed-listener tests use the real Activity engine/listener, scheduled
voiceFrame and visible view reducer with synthetic accepted-request state and
PCM. They run under Robolectric LEGACY host graphics. They do not establish the
full synthesis permission decision, audible speech, physical-device operation,
native Filament pixels or reference-image likeness. V2 additionally does not
certify pose intersections or measured human jaw anatomy.

The open Draft [#495](https://github.com/Ullmann27/device-ai/pull/495) remains on
`codex/continuum-three-avatar-helpers-20261104`, targeting #491's
`codex/astra-jarvis-takeover-20261104`. EXP-07 independent validation, EXP-11 the
existing orchestration path and EXP-12 source/checkpoint compatibility are
preserved. The completed patch changes five production Kotlin files and their
focused tests; package/signing, permissions, offline setting, model weights,
owner data and the avatar asset are unaffected. No main merge, APK, release or
installation was performed.

For rollback, revert/omit this round's scoped Activity/Director/reducer and
mapper/idle changes with their matching regression contracts, retaining the
previous verified connection checkpoint. The final source commit and the prior
checkpoint are both pinned above. Do not reset another workstream's branch.
Evidence should be retained even when reverting a change.

The full receipts, original red cases, static reviews, commands, exact source
mappings and file checksums are in
[the evidence index](../../evidence/avatar-rig-continuation-20261104/README.md).

