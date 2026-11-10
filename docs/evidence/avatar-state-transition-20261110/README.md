# Original-avatar state transition replay

WIP / UNVERIFIED. Integration owner: root, PR #715. This branch extends the existing production-pose exporter; it does not change the app renderer or GLB.

Base local `b498becd85b1e0a53a465791bf0bd63bf9a01b0c`, remote `4a8fc1e8b9d86151f80c5004abeab8991fbe2418`, identical tree `2a74e12b80a80a7e46bd65363df8a8757e5f2216`.

Original package: `app/src/main/assets/avatar3d/continuum-avatar.glb`, SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`; unchanged, 21 morph targets.

Two synthetic, silent fixed-gaze sequences warm the actual mapper for 145 samples at 60 Hz, then change IDLE to THINKING or THINKING to IDLE at 2.4 seconds. The pre-change sample and 25 post-change samples retain the same monotone clock, including a zero-elapsed-time boundary. Existing diagnostic and optional idle frames remain available.

Compiler and original-GLB replay: NOT_RUN at this checkpoint. Awaiting the verified ocular-transition source and serialized host compile/render slot. Planned PNGs use the existing Blender CPU renderer, original materials, verified neck/head split and exact exported morphs. Studio lights and framing are not Android Filament evidence. Actual Fold7, GPU, audio, microphone and camera: NOT_RUN.

Compatibility: same pose schema v2, additive frames/metadata only. No app dependency, event bus or animation lane. Rollback: revert exporter/evidence commit; package and runtime remain unchanged. Relevant existing experiment: EXP-52-CINE-DEPTH rendering verification; no learning/promotion behavior.
