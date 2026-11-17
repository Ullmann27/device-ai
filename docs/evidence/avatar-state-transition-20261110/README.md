# Original-avatar state transition replay

HOST VERIFIED. Integration owner: root, PR #715. This branch extends the existing production-pose exporter and preserves five actual original-GLB renders. It does not change the app renderer, runtime or asset. The separately implemented ocular fix is source-connected to the existing mapper; this evidence tests its pose output.

Base local `b498becd85b1e0a53a465791bf0bd63bf9a01b0c`, remote `4a8fc1e8b9d86151f80c5004abeab8991fbe2418`, identical tree `2a74e12b80a80a7e46bd65363df8a8757e5f2216`.

Original package: `app/src/main/assets/avatar3d/continuum-avatar.glb`, SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`; unchanged, 21 morph targets.

## Actual checks

Both 256 MB Kotlin compiles and exports PASS. Baseline source `3d206db052300a3d0c9f14851052dc3b8e28fba5` (published equivalent `554fc6a98f618e6181cb93f2b89880621a09ea0c`); candidate source `74892201d766d4d5c756079893642ed1547c95b8` (ocular fix published as `0aa3344beb0fed550628f0ac4f759932cd660e82`). Source hashes and compile commands are retained in the pose exports and receipt. The same committed exporter was compiled separately against each source variant.

Two synthetic, silent fixed-gaze sequences warm the actual mapper for 145 samples at 60 Hz, then change IDLE to THINKING or THINKING to IDLE at 2.4 seconds. The pre-change sample and 25 post-change samples retain the same monotone clock, including a zero-elapsed-time boundary. Existing diagnostic and optional idle frames remain available.

The executable comparison passes for 64 matching frames. Inputs, head, neck, jaw, morph weights, camera focus and hidden-node contract are unchanged. Only ocular outputs differ.

| Sequence | Previous zero-time eye yaw/pitch step | Fixed zero-time step | Largest fixed 60 Hz yaw/pitch step |
| --- | --- | --- | --- |
| IDLE → THINKING | −4.267573° / −1.352103° | 0° / 0° | 0.169392° / 0.052048° |
| THINKING → IDLE | +1.574610° / −0.274380° | 0° / 0° | 0.038059° / 0.068351° |

These are synthetic mapper values, not measured device motion. The stills complement the sampled sequence and do not establish perceived animation quality by themselves.

## Original GLB images

Blender 4.3.2, Cycles CPU, one render thread, 512×640, 32 samples, deterministic seed 731, no denoising. The existing renderer and studio lights are unchanged. All five frames pass canonical glTF skin/joint verification: maximum face position error `8.198797e-8` metres and maximum control-matrix error `2.980232e-7`, below the existing `2e-6` tolerances. Original asset and pose hashes are rechecked after replay.

- `before/idle-to-thinking-f000.png`: previous mapper, immediately after the state change.
- `after/idle-to-thinking-f000.png`: fixed mapper at that identical timestamp.
- `after/idle-to-thinking-f006.png`: fixed mapper +100 ms.
- `after/idle-to-thinking-f020.png`: fixed mapper +333 ms.
- `after/gaze-right-up.png`: existing diagnostic gaze, rendered at the same higher sample count.

Every PNG was visually inspected. Eye changes are subtle at portrait scale; the face, neck and original visual identity are preserved. Render manifests bind exact pose, camera, image hash and verification errors. Host render elapsed times were 22.7 seconds for the baseline frame and 93.8 seconds for four candidate frames; these are offline renderer times, not Android FPS, GPU or thermal measurements.

## Reproduce

Use the existing fresh-source Kotlin export recipe in `tools/avatar-3d/reference-atlas/README.md`, with this updated `ExportAvatarReviewPoses.kt`, the source variants above and idle frame count `0`. The recorded exporter hash is in `export-receipt.json`. No dependency download is required by these tools.

From the repository root, compare the committed exports:

```bash
python3 docs/evidence/avatar-state-transition-20261110/compare_pose_exports.py \
  docs/evidence/avatar-state-transition-20261110/before-poses.json \
  docs/evidence/avatar-state-transition-20261110/after-poses.json \
  /tmp/avatar-state-comparison.json
```

Replay the candidate PNGs using the unchanged renderer:

```bash
blender -b -t 1 --python-exit-code 1 \
  --python tools/avatar-3d/reference-atlas/render-production-poses.py -- \
  --asset app/src/main/assets/avatar3d/continuum-avatar.glb \
  --poses docs/evidence/avatar-state-transition-20261110/after-poses.json \
  --output /tmp/avatar-state-after \
  --expected-asset-sha256 c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a \
  --expected-poses-sha256 c07f36746f6b0eb8ac61917165a51766e9b6a20825d28770f02e1e0a0ab18aa9 \
  --frames idle-to-thinking-f000,idle-to-thinking-f006,idle-to-thinking-f020,gaze-right-up \
  --width 512 --height 640 --samples 32
```

For the previous frame use `before-poses.json`, pose hash `43bd470a09c29d616d693be316cca85b3b3157187295a243e89caaacf52fad5e`, a separate output directory and frame `idle-to-thinking-f000`. Exact original command arrays are in the receipts.

## Limits and rollback

Blender CPU pose replay with studio lighting is not Android Filament rendering. Actual Fold7, GPU, live audio, microphone and camera: NOT_RUN. No app-derived naturalness or lip-sync result is claimed. The target mapper was tested by its owner separately; its 41 host tests are not added to this export/replay count.

Compatibility: same pose schema v2, additive frames/metadata only. No app dependency, event bus or animation lane. Rollback: revert exporter/evidence commits; package and runtime remain unchanged. Relevant existing experiment: EXP-52-CINE-DEPTH rendering verification; no learning/promotion behavior. Next required acceptance: actual Filament/Fold7 transition capture, measured frame timing and owner visual review.
