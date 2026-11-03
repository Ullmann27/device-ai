# Operation Continuum Ω — native avatar lane

Status 2026-11-03: WIP / UNVERIFIED. Base PR #390: `b4f61ae9765e26dd401db928b76fa9f233c0742a`.
Branch: `codex/team-04-android-omega-20261103`; target `fix/continuum-code40-router-race-review-20261103`.

## Scope and existing sources

The current owner instruction authorizes isolated implementation and supersedes the historical v31 research freeze; it does not authorize release, signing, installation or main merge. Chief granted exclusive tiny AgentActivity attach/detach wiring. Other leased paths: avatar package, ContinuumHologramView.kt, related new tests and this handoff.

Read AGENTS, current state/frontier handoff, experiment contract/register, current Issue #41, chief Omega plan and actual PR sources. PR #338 `6f082d062e50e91cfe5bc586f2977d5b75874976` is an isolated image/TTS lab; its separate TTS/activity will not be copied. PR #350 `8d2011f34fe5786d5e9d93a50eadb3102df9dc85` provides upstream audit/viseme references, no licensed rig. PR #373 `26162442c88929738600c1862f17e24b944a44d4` provides the existing app events/reducer. PR #378 `b95d08cad3f40293743ec414fd6aaa2b5ef9270a` contains docs only. Its external workspace geometry and portrait assets are not present in this checkout and are not treated as delivered assets.

Smallest milestone: original procedural cyan/violet head drawn natively through existing ContinuumPainter, consuming AvatarDirector events and AvatarPoseReducer. Reuse existing HologramView lifecycle/power/thermal loop. Keep orb as default and provide explicit local head selection. No camera, image generation, paid services, asset download, new TTS, model, state enum or inference. Idle blink is cosmetic, never learning/research evidence. Adjacent EXP-05/EXP-11 runtime interfaces remain unchanged; no experimental compute promotion.

## Verification and next step

Checkpoint only: JUnit/Gradle/UI screenshots/Fold7: NOT_RUN. Quality lane owns SDK setup. Implement and test reducer event timing, utterance interruption/staleness, native renderer and lifecycle. Then run existing native/UI harness if available. Device performance/TTS precision remain unverified until Fold7 testing.

Rollback: revert this lane's commits or retain orb selection. No persistence migration, permission, manifest or release configuration changes.

Remote namespace: initial push of codex/team-04-android/omega-20261103 was rejected with directory/file conflict; preserved existing remote ref and renamed only this local lane.

## Native implementation checkpoint

Native head is now connected to AgentActivity's existing Director via attach when the home view is built and no-op detach on destroy. Systemstatus → AVATAR · KUGEL ODER KOPF and long-press choose the persistent local appearance; default remains original orb. The head uses original procedural 2D paths, existing AndroidContinuumPainter and the same foreground/power/thermal/reduced-motion loop. It is not a GLB/3D rig. Blink and slight sway are cosmetic. Runtime gaze/expression and measured/fallback TTS jaw remain event-driven; camera remains absent.

Fixed three baseline regressions with unchanged tests against actual #390 AvatarContract/Director: new utterance inherited previous viseme; NaN audio produced NaN jaw; replacement sink during TTS never received current utterance. Exact unchanged `AvatarExistingContractRegressionTest` baseline: 3/3 FAILED; fixed working tree: all 14 avatar JUnit tests PASS (7 existing + 7 added). Source copied from base via git show, no baseline compatibility stubs in this final RED run. New timed cue/expiry/interruption/stale callback cases pass. Log paths: `docs/evidence/omega-avatar-20261103/jvm-{baseline-red,green}.log`. Kotlin 2.4.0/JVM, OpenJDK17, JUnit4.13.2; CLI compiler from quality-provisioned dependency set. This is host logic verification, not Android build acceptance.

Existing PR #391 at `a0b19100352e1876c180af51e126a0644a70aa68` was inspected read-only: currently only design checkpoint, no delivered images. That design lane remains separate. No competing portrait generation.

Android lifecycle/Canvas tests added in `ContinuumAvatarNativeUiTest`; Gradle/UI/Fold7 still NOT_RUN pending shared Quality build slot. Fixed-size Java2D production-renderer previews are being prepared; they do not measure device performance.


## Stop-path review

Chief review checked pause/resume: existing `AgentActivity.onPause()` already stops the TTS engine, so the renderer must not resume background speech. Additional real gap repaired: `stopVoiceAnimation()` now calls existing Director.speechEnded before clearing the utterance ID, making pause/barge-in/failed-speak closure synchronous instead of waiting for onStop. The UI regression invokes this actual Activity method without onCreate/model bootstrap. This test remains NOT_RUN until the shared Gradle acceptance slot completes. No new TTS listener or state source was introduced.

Preview source and six rendered PNGs are now versioned at `docs/evidence/omega-avatar-20261103/`; original idle fixture inspected visually. Node existing avatar wiring tests: 3 PASS. Renderer paths/gradients are allocated once; drawing uses the shared painter caches. Only bounded pose/cue handling allocates at frame/event boundaries; no full-screen bitmap or blur is used in production. Fold7 FPS/thermal/battery measurement remains NOT_RUN.

## Frozen handoff to Chief / Quality

- Draft PR: https://github.com/Ullmann27/device-ai/pull/395
- Final production/test source: `29ff3a5ac7500a2f26d058795de826eb84b810b5`; https://github.com/Ullmann27/device-ai/commit/29ff3a5ac7500a2f26d058795de826eb84b810b5
- All source and evidence pushed to `codex/team-04-android-omega-20261103`; working tree clean before this documentation checkpoint.
- Quality independently reviewed this exact source and accepted isolated integration, conditional on actual combined UI tests. Chief is integrating; Quality will execute the full combined Android suite once baseline finishes. No separate competing Gradle build launched by this lane.
- Baseline full main/unit compilation reported successful by Quality; that is **baseline**, not avatar/final integration success. Avatar Android/native Canvas/lifecycle tests remain **PENDING**, not PASS.
- Committed Java2D preview harness compiled and re-executed after package/object relocation; all six PNGs reproduced byte-for-byte.
- Production scope: 4 inserted AgentActivity lines, existing HologramView optional renderer/lifecycle, existing Director/reducer fixes, new procedural renderer. No release/config/permission/manifest alterations. Existing camera behavior unchanged; new avatar has no camera access.
- Functional limits: native procedural **2D**, synthetic host visual fixtures, approximate word/viseme timing, no measured device TTS synchronization, no Fold7 performance/battery/thermal/device installation test. No inference, intelligence or learning improvement claimed.

Next gate: run `ContinuumAvatarNativeUiTest` (3 tests), `ContinuumHologramMotionUiTest`, and all `avatar.*` tests on the exact integrated tree with `-PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir=<external-output>`. Any source changes after acceptance require new source-bound results. Preserve original orb selection as immediate owner fallback.
