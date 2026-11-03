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
