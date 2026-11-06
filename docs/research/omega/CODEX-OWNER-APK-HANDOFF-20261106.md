# Continuum owner APK integration — 2026-11-06

Status: targeted preflight and original-package APK/AAB assembly GREEN. Original publisher full QA,
original signing and HTTPS release readback remain pending. This is not a release receipt.

## Sources and preserved history

- Fable #574: `642dc0fe9f5deca306a3ad1f5be40e5e3055abc5`.
- Opus #565: `cd1c072f412e722ac4933275ef0b06512d5d22b3`.
- Quality lab #573: `5a78ee19cddb1f5bd0d5b652114d3132ddc4a73f`.
- Existing Code44 publisher: `fbdffc3cf3485f6721c0091b54a6824f0c1b81fa`.
- Integration: `codex/continuum-owner-apk-20261106`, draft PR #575, base remains the Fable branch.
- Saved GitHub checkpoints: `705d35b10377e8d2ef7940f31606e180fe33882c` (combined inputs),
  `24e1a9b77e7796575abf2c71262dc3fc9c984e86` (conversation/release repairs),
  `f06ce83c026be056eca4022dfd5492f06051a2e4` (initial STOP/voice repair),
  `1a2f7b61c1aa7b1f23c7ec0b0f353d6274cba8af` (save/visual repairs and Code44 ancestry).

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
the local candidate is code45 / version0.3.44. Re-read live metadata before deployment.

## Completed implementation work

1. **Conversation and journal references.** Negated, quoted and conditional continuation text no
   longer authorizes resuming work. Shown decisions are refreshed against the actual journal and
   revoked owner statements; an edited unsaved draft is not called completed. Open work is not
   wrongly restricted to today. Standalone definitions remain standalone. Natural consequence
   questions and later references after a definition retain previous owner context. The definition
   exception is evaluated per sentence. No second memory/reasoning store was introduced.
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
| Final focused Android/JVM regression | **80 PASS, 0 FAIL, 0 SKIP**, real SDK/Gradle + Robolectric; local source `132fbd97f3225174e8d076898cc47669909af712` |
| Android debug compilation | PASS on that source |
| Android lint | PASS, existing **NewApi-only** configuration; not an all-rules lint claim |
| Final conversation pure-JVM package | **38/38 PASS**, six real production classes, no model response execution |
| Full Node integration regression | **2396 PASS, 0 FAIL, 31 SKIP /2427**, unchanged local source `80b7443da40da447c667a8ac402b9026dbcd2f8d`; later review repairs require the final publisher rerun |
| Backend | npm ci/check/test PASS, **64/64**, unchanged backend tree `f4bc00c1ba8afb0f115b26450757fd322843fd0c` |
| Quality-lab bound integration | **44/44 PASS**, source verified and unchanged at `132fbd97f3225174e8d076898cc47669909af712` |
| Local full Android intermediate run | **CANCELLED**, exit130, because later review fixes superseded its source; no complete JUnit success claimed |
| Original-package APK/AAB | PASS, code45 /0.3.44, ARM64, original package; unsigned and not published locally |
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
