# EPSILON geometry — actual local 3D research candidate

Current accepted runtime SHA256: `f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e`. Authored-animation proof GLB SHA256: `63b42c7786edadd86de22bc2afbae8638e1c61b46da887838cdaa19a34b439ca`. Actual artifacts, Blender scene and WIP render evidence are remotely preserved at [a9dc09c0](https://github.com/Ullmann27/device-ai/commit/a9dc09c0), branch `codex/living-jarvis-3d-20261103`, Draft [PR404](https://github.com/Ullmann27/device-ai/pull/404). Geometry source `9633d4e9aab997580bc0be31f20e7e10479f1433`; later `05a3e091` only adds a lower-cost review render mode.

Status: actual geometry + independent structural/runtime-metadata acceptance; visual identity, phonetic accuracy and physical Android acceptance remain open. Final visual proof is now packaged:30 requested semantic anchors represented by31 exact-runtime images (anchor20 up/down),12 exact-runtime orbit frames, contact sheet and8.8-second stepped pose/turntable video. All512px, not high resolution; **appearance NOT_ACCEPTED**. This is not smooth live animation or phone FPS evidence.

## Artifact and interface

- `assets/avatar/jarvis-runtime-v1.glb`: no authored clips, intended for existing AvatarPose-driven runtime.
- `assets/avatar/jarvis-research-v1.glb`: same geometry, with VisemeSweep, ExpressionAndSpeech and IndependentControlDemo clips for inspection.
- `assets/avatar/jarvis-research-v1.blend`: authored scene for inspection.
- Frozen names/axis convention: `docs/avatar-live/geometry-work/asset-contract.v1.json`; actual pivots, indices, hashes and counts: `asset-manifest.json`.
- The Face mesh is a continuous head/neck/shoulder bust. Skin joints are AvatarRoot, Neck and Head; separate EyeLeft/EyeRight controls parent physical eyeballs, iris and pupil meshes. Jaw is a separate lower-dental/interior control.
- Exactly21 real distinct morph targets: SIL PP FF TH DD KK CH SS NN RR AA E I O U BlinkLeft BlinkRight BrowUp BrowDown Smile JawOpen. Neutral means all zero. Exterior mouth opening uses JawOpen; rotating Jaw affects inner lower dental geometry. No double jaw application implied.
- +Y up, +Z front, +X anatomical left, metres. Rest rotations identity; local translation hierarchy has canonical pivots.

## Provenance and likeness

Viewed binding derived portrait `docs/avatar-live/reference-work/jarvis-neutral-frontal-generated.png`, SHA256 `a850c508fc5b44700024de0248b799650d160198f0e6a2039550ea266198bb78`. Original chat portraits were never invented as local files. No portrait image is projected onto the mesh or modified by the geometry scripts.

MakeHuman graphical data at immutable revision `a8bc2d54ff0ac92e78ff71431b1023eda42bf482` explicitly licenses base mesh, targets and expressions as CC0-1.0. Original scope and license statements, all immutable URLs, byte counts and SHA256 values are preserved in `tools/avatar-3d/vendor/makehuman/`. No upstream program logic imported. The local Blender builder/exporter are authored here.

The licensed base was cropped/capped into a bust, adapted to a slender oval head with reduced crown height and facial mass, widened eye apertures, neutral lip seal, natural dark irises, recessed teeth and actual cyan/violet triangular filaments. This is **partial reference adaptation**, not exact facial identity or owner visual acceptance. Rear skull/shoulder form is inferred from the base topology. Filament density/style is an approximation. No tongue choreography, production-grade FF/TH articulation, corrective facial normals or collision-free arbitrary combinations are claimed.

## Actual acceptance and defects repaired

Current asset:57,000 triangles total, ~8.69MB uncompressed runtime, no textures. Head skin19,060 triangles (exact current metric file is authoritative); fixed limits30k head/60k total. Every referenced skin weight normalizes; all21 target deltas are nonzero and unique.

Exact neutral mirror pairing: max and mean head error0, head-normalized max error0. Face topology has0 open boundary edges,0 edges with more than2 adjacent faces,0 degenerate triangles. `neutral-mirror-pairs.json` includes the complete pairing map. This is a mesh measurement, not likeness evidence.

Actual Khronos glTF-validator2.0.0-dev.3.10:0 errors and0 warnings on research and runtime variants. A structural script:62 checks passed each. Reports under `docs/avatar-live/geometry-work/` and `runtime/`.

B independently froze executable protocol771aec34 before inspecting the candidate, then reported exact runtimef39a89fc… PASS unchanged geometry checker (42,292 per-primitive vertex references,57,000 triangles,3 morphed primitives), plus actual Android metadata preflightPASS. B retains independent evidence and mutation controls in its own branch. These results do not certify Android GPU rendering, phone FPS/thermal behaviour, likeness, viseme phonetics or owner acceptance.

Earlier failures remain recoverable: first exporter998 warnings preserved in `khronos-validator-first-export.json`; original authored clips fail B's event-driven preflight; an explicitly clip-stripped diagnostic still failed mouth locality. Source mouth expression deltas moved upper face by ~0.5mm. The fix applies a smooth speech-region mask (full belowY.245m, exact zero at/aboveY.260m) after barycentric topology transfer, and again to filaments. Eyelids and brows remain independent. Earlier GLB hashes are preserved in commits060a506b and17d73451.

A transient topology reduction had unmatched neutral vertices; exact half-mirror + seam weld repaired this before final acceptance. Mirror seam artifacts were removed; final topology metrics above were measured on the resulting mesh. Raw interrupted/failed Blender logs remain preserved, not presented as successful builds.

## Reproduce and resume

```sh
# Blender4.3.2, bounded CPU2; nice10 yields to active Gradle work.
nice -n 10 env OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=2 blender -b -t 2 --python-exit-code 1 --python tools/avatar-3d/build_bust.py -- --quick-views
node tools/avatar-3d/derive-runtime.mjs
node tools/avatar-3d/validate-glb.mjs assets/avatar/jarvis-research-v1.glb docs/avatar-live/geometry-work
node tools/avatar-3d/validate-glb.mjs assets/avatar/jarvis-runtime-v1.glb docs/avatar-live/geometry-work/runtime --runtime
python tools/avatar-3d/assemble_review.py
```

`-- --export-only` rebuilds without rendering. Default rendering is640px/32samples; `--quick-views` is512px/16samples, no denoiser (this Blender lacks OpenImageDenoise). `--preview` creates four diagnostic views, `--expressions-only` recreates the six expression views. `roundtrip_check.py` deletes source meshes then imports the real runtime GLB before rendering; its execution evidence must be checked separately. Node validator dependencies are pinned; `npm ci --ignore-scripts` only in `tools/avatar-3d`. The game-dev CLI is unavailable; no paid job, login, credit purchase or provider retry occurred.

Completed exact-runtime proof: `docs/avatar-live/geometry-work/runtime-anchors/README.md`, `semantic-contact-sheet.png`, `runtime-proof.mp4`, `runtime-anchor-manifest.json`, raw `runtime-anchors.log`. Renderer source81b22f54 imports exactf39 after deleting all source meshes/rigs. Existing `contact-sheet-30.png` is a separate24-orbit+6-expression source-scene sheet, not the requested30 semantic anchors. No ImageGen face substitutions.

Full procedural GLB rebuild byte identity **FAILED**: research49df… vs canonical63b… due rigid UV-sphere triangle ordering and normal float rounding (~1.5e-7). The entire JSON, every mesh POSITION array, all triangle sets ignoring order, and all morph data match exactly; `reproduction.json` and `rebuild-diagnostics/` preserve evidence. The accepted canonical63b/f39 files were restored unchanged. Runtime derivation from frozen63b is byte reproducible; do not claim identical bytes from a fresh procedural Blender rebuild.

Independent B final source/evidence7caef9b6, PR405, reports `A-V2-INDEPENDENT-VALIDATION.json` and `A-V2-ANDROID-PREFLIGHT.json` under `docs/evidence/epsilon-android-20261103/`. B visual review separately rejects reference appearance (wire density/material/eyes/lips). Anchor24 stresscase AA1+JawOpen0+innerJaw12.6° visibly protrudes lower teeth; it does not reproduce actual mapper AA+JawOpenq+Jaw12q°. B subsequently rendered the actual mapper AA1+JawOpenq+Jaw12q° for q0/0.5/1: no lower dental protrusion in those three inspected frames. No app repair indicated by these samples; no all-pose collision-free claim. No new modeling cycle is authorized in this closing package.

Next owner/design gate: refine reference appearance and phonetics with explicit visual acceptance; then exact-app GPU/lifecycle/thermal/device checks. Current asset is reviewable research, not finished identity or release.

Scope boundary: no Android Kotlin, Activity, Gradle, main, signing, release or installation changed. Existing AvatarDirector/Sink/PoseReducer remain the sole interface. Rollback: omit this optional research asset and tool directory; default orb and brain paths are unaffected.
