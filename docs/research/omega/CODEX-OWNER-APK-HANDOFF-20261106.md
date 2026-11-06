# Continuum owner APK integration — 2026-11-06

Status: **DELIVERED — original-signed Code46 /0.3.45 is live and independently verified.**
Release source: `577f0931e7937ffcac02dc855a540f093facdb3b`. Publisher QA, signing, HTTPS
readback and actual APK certificate/package/version/hash verification passed. Final verification:
**2026-11-06T19:03:21.720300Z**. The release receipt is `code46-release-verified.json` below.

[Download the verified APK](https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-0930614bbcc8e53d75283dfe47a53a73e758430a99af3698f31b88446babc759.apk)

The existing in-app “Updates prüfen und laden” button reads the live catalog containing this APK.
No device installation or real-model/audio acceptance is implied by this delivery.

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
Signing credentials stay on that service. The delivered live release is code46 / version0.3.45.
Any future release must re-read the live catalog and use a greater version code.

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
| Complete final-source Android/JVM regression | **3539 PASS, 0 FAIL, 20 SKIP /3559**, **626 suites**; exact GitHub source `577f0931e7937ffcac02dc855a540f093facdb3b`; all JUnit XML fresh; complete XML and log archived |
| Final-source compilation and lint | PASS; existing **NewApi-only** lint, zero reported issues; complete Gradle invocation exit0 |
| Final focused Android/JVM regression | **83 PASS, 0 FAIL, 0 SKIP**, real SDK/Gradle + Robolectric; clean local source `d57a8ce093efe4c37491116bb954abe0a0bfa434` |
| Android debug compilation | PASS on that source |
| Android lint | PASS, existing **NewApi-only** configuration; not an all-rules lint claim |
| Final conversation pure-JVM package | **41/41 PASS**, six real production classes; three final regression REDs reproduced before the one-line fix; no model response execution |
| Full final-source Node integration regression | **2396 PASS, 0 FAIL, 31 SKIP /2427**, exact unchanged GitHub source `577f0931e7937ffcac02dc855a540f093facdb3b`; real Kotlin compiler cases included; no placeholders |
| Backend | npm ci/check/test PASS, **64/64**, unchanged backend tree `f4bc00c1ba8afb0f115b26450757fd322843fd0c` |
| Quality-lab bound integration | **44/44 PASS**, source verified and unchanged at final GitHub source `577f0931e7937ffcac02dc855a540f093facdb3b` |
| Local full Android intermediate run | **CANCELLED**, exit130, because later review fixes superseded its source; no complete JUnit success claimed |
| Original-package APK/AAB | PASS, code46 /0.3.45, ARM64, original package; APK/AAB archive integrity verified; unsigned and not published locally |
| Final publisher full gates/signing/readback | **PASS** on exact source `577f0931e7937ffcac02dc855a540f093facdb3b`; live deployment, downloaded bytes and original certificate independently verified |
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

Next: accept this exact delivered APK on Fold7, then tackle the
next confirmed gap using one RED→fix→GREEN milestone. Prioritize remaining AppCrystal/work-step
coupling and camera return; tool journal evidence; decision digest into the existing model prompt;
actual offline voice/lip synchronization measurements. Preserve OFF/ASK/ALWAYS_ON tool policies.

The cinematic target remains the owner's original: spatial app crystals, transparent holographic
work/document windows, light paths, reflective glass and gradual light transitions; eyes move to
the actual work window before the original head follows naturally; the original orb responds to
real work and memory events. No replacement avatar, static substitute, invented tool action or
palette change. Host screenshots are layout evidence, not a Fold7 or Filament GPU acceptance.

## Historical preflight and release checkpoints

The following checkpoints preserve the progression and failed attempts. Their then-pending
release steps are superseded by the final verified delivery section at the end of this document.

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

## Original publisher candidate before the depth-test harness correction

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

## Full publisher RED and depth-test harness repair

Code45 deploy `dep-dbn06r8u01pc73fpcgt0` failed at 2026-11-06T17:27:26.425591Z.
The full Android run reported **3556 total, 5 failed, 20 skipped**; all five failures came from
`JarvisWorkWindowDepthTest` calling `RectF.width()` in a plain AGP JVM test. The independent
local reproduction also produced exactly 5 FAIL /1 PASS and “Method width ... not mocked”.
The publisher recorded three red gates and blocked original signing and publication.

This does not invalidate Fable’s specifically described non-Gradle host result: that runner
provided the Java framework implementation through `android-all-15.jar`. The real SDK runner
uses a mockable Android JAR. The test class now belongs to the existing `src/uiTest` source set
and uses the existing Robolectric runner with SDK28. All six test methods and 58 assertion calls
are unchanged; production code and rendering are unchanged. Class-body SHA256:
`31638559191dd4ac00e458733a28c8c24acd9e589b81c6a1e5cfa2a05b1985cc`.

Targeted SDK GREEN: **6/6 PASS, 0 FAIL, 0 SKIP**, local commit
`7ce9eacee3ef2ac46fce995a2455a7a4d3c4d031`. The earlier 83-case and unsigned Code46 preflight
production trees remain unchanged by this harness-only fix. Complete source-bound regression
and a new corrected publisher candidate are still required. The old Code46 deploy
`dep-dbn0gj5s25qs73a1vrv0` started on the superseded source at 17:27:26.791398Z and does not
contain the harness repair. It must not be reported as the corrected final release.

## Corrected Code46 source and full regression

The publisher and integration branches received the same unchanged Robolectric class through
GitHub commits `72b508b7b`, `a7775d9b2`, `3b63cc7f4`, and final corrected candidate
`577f0931e7937ffcac02dc855a540f093facdb3b`. Its production and test files match the locally
verified 6/6 harness fix. The concurrent documentation is preserved in
`render-code45-android-regression-20261106.md` and this merge history.

Corrected deploy: `dep-dbn0u78gs6ms73a7m54g`, queued at 2026-11-06T17:32:14.5897Z.
The prior running deploy `dep-dbn0gj5s25qs73a1vrv0` is superseded and lacks this fix.
No second trigger was issued for the already queued corrected candidate.

Source-bound quality-lab rerun on 577f0931e7937ffcac02dc855a540f093facdb3b: 44/44 PASS,
source verified and unchanged. The complete local SDK/Robolectric/lint run on an isolated clean
checkout of that exact GitHub commit subsequently passed; the final evidence is recorded below.

## Corrected-source host preflight checkpoint

On `577f0931e7937ffcac02dc855a540f093facdb3b`, the full Node run completed with
**2390 PASS, 0 FAIL, 37 SKIP /2427**, unchanged source and confirmed process termination.
Six extra skips compared with the earlier 31 were real-Kotlin compiler checks: `kotlinc` was
not on that invocation’s PATH. Those six checks subsequently passed in the final full Node
run below, after Android completed and without concurrent compiler workloads.

The first additional complete local Android attempt ended with exit 1 because its Gradle daemon
disappeared. No complete JUnit result or concrete assertion failure was emitted. No JVM crash
file or OutOfMemoryError was found; kernel memory counters were inaccessible. This is
`INFRA_ABORT_CAUSE_UNDETERMINED`, never a PASS and not a proved OOM. The same unchanged
source subsequently passed sequentially with the existing 1-GiB test worker, one Gradle worker
and a 1600-MiB Gradle heap. No product or publisher gate was changed.

## Complete corrected-source Android GREEN

The complete unfiltered invocation on `577f0931e7937ffcac02dc855a540f093facdb3b` finished
successfully in 35m21s: **3539 passed, 0 failed, 20 skipped /3559**, across **626 JUnit suites**.
All XML files were written after the saved run start, and the checkout remained clean.
Compilation and the existing NewApi-only lint configuration passed; lint reported zero issues.
The exact counts, toolchain, command properties, skipped names and hashes are in
`docs/evidence/codex-owner-apk-20261106/android-full-code46-green.json`. The accompanying
`android-full-code46-junit.zip` preserves all 626 XML files, lint XML and the complete Gradle log.

The skips are explicit: twelve unfinished Chief E2E acceptance cases; two disabled-by-default
Chief/Node/Docker host integrations; three unconfigured Node-to-Kotlin parity-data checks;
one optional visual host export; and two LiteRT lifecycle tests requiring JDK21, while this
Android build uses JDK17. Several historical Chief dependency comments are stale: these twelve
skips prove missing E2E acceptance, not twelve entirely absent product components. Detailed
classification: `docs/evidence/codex-owner-apk-20261106/android-full-code46-skips.md`.

The earlier Code46 publisher `dep-dbn0gj5s25qs73a1vrv0` failed on the same five plain-JUnit
RectF cases at 2026-11-06T18:12:20.408165Z. The first corrected-source queue entry
`dep-dbn0u78gs6ms73a7m54g` then stopped at the source-specific release-freeze guard, before QA,
at 18:12:43.485742Z. Both existing source-authorization variables were bound to the owner's
authorized exact candidate, preserving signing credentials and all QA gates. The resulting
corrected run is `dep-dbn1htjncjis73c9vlng`, started at **18:14:14.342126Z**. Its backend gate
passed at **18:17:53.384462019Z**; signing and live-download verification remain pending here.

## Complete corrected-source Node GREEN

The final full Node run on the same frozen source started at **18:32:37.773Z** and completed
at **18:35:58.032Z**: **2396 passed, 0 failed, 31 skipped /2427**, zero placeholders,
exit0, confirmed process termination, and unchanged source. `kotlinc-jvm 2.4.0` and JDK17
were available on PATH. All six earlier compiler-related skips now executed and passed:
real generated Kotlin RED/GREEN compilation, independent output oracles, rejection of wrong
expected outputs and an unrelated RED baseline, and creation/rejection of owner Kotlin previews.

The remaining 31 skips require additional Docker/kernel isolation, SDK/JVM dependency or
fixture configuration. Their exact original messages are preserved in `node-full-code46-green.json`.
`node-full-code46-evidence.zip` contains the complete source-bound report, JUnit and runner log.
The report generator's historical `generatedBy` label is metadata, not a claim that another
agent ran this session. Android, signing and device evidence remain separate scopes.

## Final verified delivery

Original publisher deploy **`dep-dbn1htjncjis73c9vlng`** reached **live** at
**2026-11-06T19:00:55.5398Z**. Its exact-source presign QA gate passed at **18:58:52.360489958Z**;
the signed catalog/staging checks passed at **19:00:52.811001149Z**. All signing credentials,
the original host and package identity were preserved.

The published APK was downloaded over pinned HTTPS. Two catalog reads agreed; actual file size
and SHA256 matched the catalog and BUILD-INFO. Independent Android SDK `apksigner` and `aapt2`
both exited zero and verified the APK bytes, not only metadata.

| Item | Verified value |
|---|---|
| Release source | `577f0931e7937ffcac02dc855a540f093facdb3b` |
| Version | `0.3.45`, versionCode `46` |
| Package | `at.heinz.deviceai.continuumlocal` |
| ABI | `arm64-v8a` only |
| Downloaded size | `101876503` bytes |
| APK SHA256 | `0930614bbcc8e53d75283dfe47a53a73e758430a99af3698f31b88446babc759` |
| Original certificate SHA256 | `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` |
| Signature | APK Signature Scheme v2 verified, one signer |
| Catalog SHA256 | `6b238e9ae2b326ae9d24ff9c10d9a49c71c62c2cfc997fc8612716cb69dd694c` |
| Original head inside the actual APK | `assets/avatar3d/continuum-avatar.glb`, SHA256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`; matches preserved Fable/Opus asset |

Complete evidence lives in `docs/evidence/codex-owner-apk-20261106/`:
`code46-release-verified.json`, `publisher-code46-live.json`, live catalog, BUILD-INFO,
HTTPS readback and actual apksigner/aapt2 outputs, plus the complete Android/Node archives.
The evidence checkpoint preceding this final record is
`761f2f0d88c757d8b0f88abedcc1d9034fbf8f9c`; the release source stays frozen separately.
The final documentation commit is reported in PR #575 and the delivery message.

Delivery covers the repaired current code baseline. The explicitly skipped host tests, actual
Fold7/model/audio acceptance and the remaining visual/autonomous milestones listed above stay
open. No main merge, replacement key, package/publisher change or extra paid service was used.
