# NAVADA live integration

**Goal:** Integrate the existing holographic portrait, live speech and optional local camera observations into the current Continuum Android application, preserving its brain visualization, authorization and memory paths.

**Pinned base:** `6cb9e760f0a16a6f34a8442abf9b79dcf2705799` (PR #502), stacked on PR #500. Working branch: `feature/continuum-navada-live-integration-20261104`.

**Architecture:** Extend `at.heinz.deviceai.avatar`, `avatar3d`, the existing camera input and `AgentActivity`. Keep `avatar3d/continuum-avatar.glb`, its 21 canonical morph targets and the existing single view-owned render clock. Do not introduce the suggested second renderer, scheduler, memory store or unsafe force-reset worktree manager. Application ID and signing/update paths retain their existing configuration.

## Execution sequence

1. Preserve an early checkpoint and stacked draft PR; inspect original handoffs, asset provenance, contracts, lifecycle and existing tests. Record installed tools and evidence boundaries.
2. Render the actual baseline GLB with Blender. Improve the asset/material/framing only with reproducible scripts and compatible morphs. Keep eye, nose and lip contours readable, with violet on viewer-left and cyan on viewer-right. Compare to the four owner-supplied images.
3. Add independent regressions for vowel-to-bilabial closure, repeated/backward timestamps, completed utterance reuse, STOP and session isolation. Repair the existing mapper/director. Inspect the certified-answer boundary before enabling sentence speech.
4. Add an optional local Face Landmarker adapter and observation geometry. Tracking stays disabled until explicit in-app opt-in and camera permission; invalid/stale frames and revocation clear observations. Describe visible geometry and movements without claiming identity, emotion, measured physical dimensions or exact ocular gaze.
5. Wire the actual native portrait and camera/speech callbacks through existing UI/lifecycle interfaces. Preserve the Continuum brain, make foreground/background and surface handling explicit, and avoid continuous decorative head wobble.
6. Run focused regressions, GLB contract validation, Android compilation/unit tests/lint when supported. Produce genuine Blender renders and, only if an Android device/emulator is actually connected, native screenshots/video. Keep host, emulator and physical Fold7 results separate.
7. Publish reviewable commits on this branch, record source/asset hashes, limitations and rollback in the handoff. Do not merge, release, sign or install an owner update from this work.

## Ownership during implementation

- Root: repository/PR checkpoints, asset and Filament renderer, central Activity/dependency wiring, evidence and integration review.
- Motion worker: existing speech director/contract, mapper mouth/time logic and their regression tests; proposes Activity hooks.
- Camera worker: tracking/observation classes and tests; proposes dependencies and Activity hooks.
- Build worker: local toolchain, baseline/final build and test execution; no competing source edits.

## Validation and constraints

- Relevant experiment interfaces: EXP-00 Android local execution; EXP-05 existing local model runtime; EXP-08 authorized device bridge; EXP-11 existing specialist/work queue; EXP-12 continuity/rollback. This work does not promote learned weights or replace those interfaces.
- The four reference images are available in the session as `01-59477.png` through `04-59351.png` (JPEG content despite their suffixes). No reference video has been observed in this session. Animation timings derived from prose are design decisions, not a claimed video analysis.
- Occluded head geometry is a documented symmetric reconstruction assumption. A Blender image cannot prove Android rendering or real tracking.
- 60–120 fps, sub-1.5-second speech onset and sub-40-ms stop are measurement targets. Actual hardware measurements are required before marking them achieved.
- Existing GLB hash before edits: `5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`. Read and preserve asset licensing/provenance.
- Baseline host tools observed: Blender 4.3.2, Java 21, Node, Python, ffmpeg and Git. Android SDK/ADB/Gradle availability is under verification; no physical Fold7 connection has been demonstrated.

## Completion gate

An honest handoff must name every changed integration, exact commands and exit codes, any unavailable environment, the final source commit and asset digest. A working source integration is not labeled a verified owner release.
