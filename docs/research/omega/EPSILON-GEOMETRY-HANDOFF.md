# EPSILON geometry — research checkpoint

Branch `codex/living-jarvis-3d-20261103`, Draft PR404, initial source `3ca9df08`.

Scope: actual local Blender bust and facial morph research, explicit owner authorization through root. No Android source, Gradle, Activity, main, signing, installation, release, paid jobs, or login. Existing AvatarDirector/Sink/PoseReducer contract is unchanged.

Viewed the binding derived portrait `docs/avatar-live/reference-work/jarvis-neutral-frontal-generated.png`, SHA256 `a850c508fc5b44700024de0248b799650d160198f0e6a2039550ea266198bb78`. Original chat portraits are not invented local files. No finished likeness claim.

Upstream MakeHuman data at immutable revision `a8bc2d54ff0ac92e78ff71431b1023eda42bf482` explicitly licenses base mesh, targets and expressions as CC0. `tools/avatar-3d/vendor/makehuman/provenance.json` records every downloaded byte count, SHA256 and immutable URL; original license statements preserved. Only graphical data imported, no upstream program logic. Independently authored local Blender builder is next.

Planned schema: `docs/avatar-live/geometry-work/asset-contract.v1.json`; 15 OVR targets plus BlinkLeft/BlinkRight/BrowUp/BrowDown/Smile/JawOpen. Anatomical left is +X from a +Z camera. Independent challenger receives schema only before its freeze, no candidate outcomes.

Environment evidence: Blender 4.3.2, Node24.19.0, ffmpeg available. `game-dev` CLI unavailable, so its packaging workflow cannot run; local Blender plus Khronos validator will be used directly under existing authorization. No mesh generated yet. Tests/render/import/device all NOT_RUN.

Next: generate actual geometry, inspect front/profile/back and expression renders, preserve reproducible build/export/validation scripts, create 30 same-mesh views and turntable within 2 CPU threads. Rollback: omit research asset/tools; no app is changed.

## Local candidate checkpoint after 06bd9d91

Authored `build_bust.py` and ordinary glTF exporter now produce actual CC0-derived 3D head/neck/shoulder geometry, distinct eyeballs/irises/pupils, recessed dental arches, 21 real morph targets, 3-joint smooth neck/head skin, and morph-bound physical triangular filaments. Shape keys use licensed source expression deltas plus authored combinations. This is a research candidate with partial reference adaptation, **not exact identity or completed visual acceptance**.

First raw glTF validation finds zero errors but many zero-weight-joint warnings and non-root skinned mesh warnings. These are being repaired; the preserved first report is `khronos-validator-first-export.json`. Original denoising and first custom-export errors are preserved in raw logs. No physical phone render/performance result. Candidate total87,618 triangles (head skin32,452 before cutoff definition refinement), so the initial60k-total goal is NOT met. Textures0, uncompressed GLB ~13.65 MB. Production LOD remains open.

All node/target names remain the frozen contract. Actual glTF geometric origin is base center, +Y up/+Z forward, anatomical left +X. Runtime must use `JawOpen` for exterior skin; `Jaw` rotation is separate lower inner-mouth geometry. Source Blender scene is rendered directly; glTF roundtrip rendering is still to run.
