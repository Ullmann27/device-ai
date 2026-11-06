# Continuum owner APK integration — 2026-11-06

Status: targeted preflight and original-package APK/AAB assembly GREEN. Original publisher full QA,
original signing and HTTPS release readback remain pending. Final candidate: code46 /0.3.45.
This is not a release receipt.

## Sources and preserved history

- Fable #574: `642dc0fe9f5deca306a3ad1f5be40e5e3055abc5`.
- Opus #565: `cd1c072f412e722ac4933275ef0b06512d5d22b3`.
- Quality lab #573: `5a78ee19cddb1f5bd0d5b652114d3132ddc4a73f`.
- Existing Code44 publisher: `fbdffc3cf3485f6721c0091b54a6824f0c1b81fa`.
- Integration: `codex/continuum-owner-apk-20261106`, draft PR #575, base remains the Fable branch.
- Saved GitHub checkpoints: `705d35b10377e8d2ef7940f31606e180fe33882c` (combined inputs),
  `24e1a9b77e7796575abf2c71262dc3fc9c984e86` (conversation/release repairs),
  `f06ce83c026be056eca4022dfd5492f06051a2e4` (initial STOP/voice repair),
  `1a2f7b61c1aa7b1f23c7ec0b0f353d6274cba8af` (save/visual repairs and Code44 ancestry),
  `48251610d9c9519678e9fc5ef652ad8286675b7f` (review repairs and Code45 preflight;
  superseded by the three final reviewed follow-up variants in Code46).

Both original handoffs survive. Fable's final record remains
`docs/handoff/FABLE51_CONTINUATION_20261106.md`; Opus's earlier handoff is archived as
`docs/handoff/OPUS_TO_FABLE_BASELINE_20261106.md`. Their differing handoff was a documentation
collision, not a reason to remove either implementation. The compatible Code44 release contracts
and 1-GiB UI test worker are retained; the newer Opus measured work-window layout is retained.

Local test commits in receipts are explicitly local anchors. GitHub API checkpoint commits have
different commit IDs; matching Git trees and source hashes connect those checkpoints. The final
publisher must rerun its complete gates on its own exact, recorded GitHub SHA.

## Authorization and release invariants

The owner authorized repairs, regression checks, original signing and delivery through the existing
download channel. No merge into main, package/signer/publisher replacement, extra paid service or
removal of existing work. Package: `at.heinz.deviceai.continuumlocal`.

Existing publisher: `srv-dbf1tr2vcj2c73ehpfog`, host
`https://continuum-owner-v23-release-preflight.onrender.com`, branch
`research/continuum-m8-render-signed-release-20261031`, auto-deploy disabled.
Original certificate SHA-256:
`6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.
Signing credentials stay on that service. Release must use a version greater than the live catalog;
the final local candidate is code46 / version0.3.45. Re-read live metadata before deployment.

## Completed implementation work

1. **Conversation and journal references.** Negated, quoted and conditional continuation text no
   longer authorizes resuming work. Shown decisions are refreshed against the actual journal and
   revoked owner statements; an edited unsaved draft is not called completed. Open work is not
   wrongly restricted to today. Standalone definitions remain standalone. Natural consequence
   questions and later references after a definition retain previous owner context. The definition
   exception is evaluated per sentence. The reviewed particles “überhaupt”, “hier” and
   “praktisch” also retain expanded previous-owner context. No second memory/reasoning store was introduced.
2. **STOP and lifecycle.** Queued journal/decision/PDF callbacks are rejected after actual STOP or
   session/lifetime invalidation. STOP clears pending document intent before early returns. Ordinary
   pause/resume and additional messages do not silently cancel previously requested work. Empty
   decision replies clear old focus. A discarded PDF page bitmap is recycled.
3. **Saved-draft identity.** `PendingDraftSave(text, episodeId)` freezes the destination journal
   episode before Android's save picker. A later current episode cannot inherit another draft,
   saved status or URI. An unbound draft remains unbound; picker cancellation leaves both episodes.
4. **Offline voice selection.** Selection checks actual `setVoice` success and the engine's selected
   offline voice; unusable candidates fall back to another available offline candidate. Muted audio
   does not turn a successful selection into a false failure message. No new speech engine.
5. **Existing work animations.** Leaving cards, moving card bodies/neon rims, command chips,
   entrance trails, live work lines/points, halos, rings and sparks respect head/orb regions.
   Circle-ring and segment checks preserve clear interiors/diagonals. Existing materials, timing,
   original avatar/orb assets and cyan/blue/violet/magenta palette remain the rendering architecture.
6. **Release and evidence correctness.** HTTPS readback accepts the established 2-GB maximum rather
   than incorrectly rejecting the existing approximately 102-MB APK at 90MB. Source contracts follow
   current real wiring. Quality-lab reports require the exact observed source and an unchanged,
   committed checkout; old unbound results cannot be relabeled as another commit's PASS.

Relevant interfaces: `WorkDecisionRecall`, `WorkRecall`, `LocalConversationFollowUpPolicy`,
`AgentActivity` journal/PDF/cancellation/voice callbacks, `JarvisVoiceSelection`,
`JarvisSequenceEffects`, existing release readback and `tools/fold7_quality_lab`.

## Actual validation evidence

| Check | Result and exact scope |
|---|---|
| Final focused Android/JVM regression | **83 PASS, 0 FAIL, 0 SKIP**, real SDK/Gradle + Robolectric; clean local source `d57a8ce093efe4c37491116bb954abe0a0bfa434` |
| Android debug compilation | PASS on that source |
| Android lint | PASS, existing **NewApi-only** configuration; not an all-rules lint claim |
| Final conversation pure-JVM package | **41/41 PASS**, six real production classes; three final regression REDs reproduced before the one-line fix; no model response execution |
| Full Node integration regression | **2396 PASS, 0 FAIL, 31 SKIP /2427**, unchanged local source `80b7443da40da447c667a8ac402b9026dbcd2f8d`; later review repairs require the final publisher rerun |
| Backend | npm ci/check/test PASS, **64/64**, unchanged backend tree `f4bc00c1ba8afb0f115b26450757fd322843fd0c` |
| Quality-lab bound integration | **44/44 PASS**, source verified and unchanged at final GitHub source `756bc68d0fb7f8522da8900fd889fd2eae2e8caf` |
| Local full Android intermediate run | **CANCELLED**, exit130, because later review fixes superseded its source; no complete JUnit success claimed |
| Original-package APK/AAB | PASS, code46 /0.3.45, ARM64, original package; APK/AAB archive integrity verified; unsigned and not published locally |
| Final publisher full gates/signing/readback | PENDING |
| Actual model responses, real audio, Fold7/GPU | NOT_RUN |

The31 Node skips concern Docker/kernel isolation or additional host test prerequisites. They are
not counted as passes. The initial saved-draft RED fixture failed to pump paused frames: its two
failures were discarded as product evidence. The corrected fixture reproduced three actual journal
misassignments, then all four save tests passed. The initial lifecycle expansion was also corrected:
four pause/new-message REDs were reproduced and now pass while STOP controls remain green.

Receipts: `docs/evidence/codex-owner-apk-20261106/` and
`docs/evidence/codex-apk-conversation-20261106/`. The historical quality-lab acceptance document
now explicitly marks its LOCAL_UNCOMMITTED results as unbound; its original JSON is preserved.

## Functional boundaries and continuation order

- **Long-term memory / WorkDecisionRecall:** existing stores and verified journal-based recall stay
  intact. Decisions from journal/ledger still do not enter the free-model prompt as a decision digest;
  the separate journal/recall answers are preserved.
- **Free context and logic:** routing and prompt-frame evidence improved; actual local LiteRT answer
  quality has not been established by host fixtures. Use the holdout quality lab with actual model
  replies and explicit inference provenance.
- **Autonomous work:** existing tool gates remain. `CockpitWorkJournal.Kind` still covers DOCUMENT;
  tool execution journaling and more complete work evidence are a later bounded milestone.
- **Voice / lips:** original voice picker, offline policy and lip/text/audio fallbacks remain.
  Real female German voice naturalness, timing, latency and engine choice require actual engine
  output. Existing research is not evidence of a newly installed engine or phoneme timing.
- **Original 3D head / orb / holograms:** Filament, cockpit, camera/work-window and gaze architecture
  remain. The guards above repair overlays; they do not claim completion of the outstanding
  AppCrystal light-path expansion or a new camera-return choreography.

After final publisher verification and delivery: accept that exact APK on Fold7, then tackle the
next confirmed gap using one RED→fix→GREEN milestone. Prioritize remaining AppCrystal/work-step
coupling and camera return; tool journal evidence; decision digest into the existing model prompt;
actual offline voice/lip synchronization measurements. Preserve OFF/ASK/ALWAYS_ON tool policies.

The cinematic target remains the owner's original: spatial app crystals, transparent holographic
work/document windows, light paths, reflective glass and gradual light transitions; eyes move to
the actual work window before the original head follows naturally; the original orb responds to
real work and memory events. No replacement avatar, static substitute, invented tool action or
palette change. Host screenshots are layout evidence, not a Fold7 or Filament GPU acceptance.

## Final Code46 preflight

The final three review cases were reproduced as 3 FAIL /38 PASS, then passed as 41/41
pure-JVM cases. The integrated SDK run passed 83/83 on the clean local source
`d57a8ce093efe4c37491116bb954abe0a0bfa434`; compile and NewApi lint also passed.
Unsigned APK and AAB assembly completed on that same clean source. Their concrete hashes,
package, version, ARM64 ABI and CRC results are in `code46-unsigned-build.json`.

Code45 publisher deploy `dep-dbn06r8u01pc73fpcgt0` was started from the earlier checkpoint.
Code46 supersedes it and must complete its own exact-source gates before final delivery.
The successful publisher log exposes gate markers, not full Node/Android totals. Do not
relabel local totals as Render totals. Final delivery requires the actual Code46 manifest,
BUILD-INFO, downloaded bytes and independent original-certificate verification.

## Original publisher candidate

Frozen Code46 source: `756bc68d0fb7f8522da8900fd889fd2eae2e8caf`; exact tree
`2161fc5f7dd2b45bba1ac42a59c00746d7611746` matches the locally verified preflight tree.
Both integration and publisher branches were fast-forwarded to that source before scheduling.
Deploy `dep-dbn0gj5s25qs73a1vrv0` was queued at 2026-11-06T17:03:09.783024Z through
the existing service configuration update. The original signing credentials and host remain.
The final-source quality-lab rerun passed 44/44 with source verification and an unchanged checkout.

The existing in-app “Updates prüfen und laden” button reads the fixed `continuum-local/latest.json`
and downloads its hash-bound APK URL. No button change is needed. The public root URL is not an
HTML download page; the final direct APK link must be taken from the verified live catalog.

This documentation checkpoint does not advance the frozen publisher branch or restart its build.
A later release receipt must record the actual deployment outcome and downloaded APK verification.

## Full publisher Android regression 2026-11-06

Render Code45 source `48251610d9c9519678e9fc5ef652ad8286675b7f` failed full Android: **3556 total, five `JarvisWorkWindowDepthTest` failures, 20 skips**, three QA_RED gates. Signing and download were blocked as designed. Existing Code46 pin `756bc68d0fb7f8522da8900fd889fd2eae2e8caf` still contains the plain-JVM `android.graphics.RectF` tests. Migrate the **complete unchanged assertions** into `src/uiTest` using Robolectric SDK28, matching the publisher's existing `-PcontinuumUiWidgetTests=true` full test path; keep production code, original signer, package and gates untouched. See `docs/evidence/codex-owner-apk-20261106/render-code45-android-regression-20261106.md`. This is a source correction, not a green build or release. The corrected exact publisher source requires a new full QA and signed readback.
