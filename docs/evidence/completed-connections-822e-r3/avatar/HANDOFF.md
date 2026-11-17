# Avatar and landscape integration

Experiment: `COMPLETED-CONNECTIONS-822E-R3-AVATAR-LAYOUT`.

## Completed landscape source

Imported #524 at `235d764f790dee7685c6590cf0a5d07d1fafdce2` (fix `652e6e30e63a427546c6a30988906c351406be25`). The production file and five small test/reproduction files match the source Git blobs exactly; see `provenance.json`.

Only production change: round a presentation pill bottom outward by one Float ULP if subtraction would produce a height below 48 dp. HERO bounds and original test assertions are unchanged. The three new precision checks are invoked by JUnit through `ContinuumLandscapeTargetPrecisionTest`.

Actual local checks: all six blob identities PASS; original `ContinuumLandscapeCockpitLayoutTest.kt` unchanged PASS; `git diff --check` PASS. Host replay, Android JUnit, and lint have not run in this integration checkout. Historical source evidence is not a new integration result. Parent owns the shared Android gates.

Source handoff: https://github.com/Ullmann27/device-ai/blob/235d764f790dee7685c6590cf0a5d07d1fafdce2/docs/research/omega/G1-R6-LANDSCAPE-832-REPAIR-20261104.md

Compatibility: no Activity, renderer, lifecycle, fallback, motion, model, or shared build configuration edits. The small host-only constants fixture stays outside production sources.

Rollback: revert layout commit `b94a4ae06deb8c90b361a0273d7d4bddd48304f6`; no data migration.

## Completed packaged reference avatar

Imported the completed packaged reference GLB exposed by #521 pin `2350866904cb82484148c0c8c63416fa51034269`. Its remote Git tree `c26e1f73d49901c7e9beb6f5fe8b639e5736702a` was verified through GitHub's Git-commit endpoint and matches committed local twin `a6f0b09bf53229ef40f039e0418e811852d32a11`. Every imported file was extracted from that committed tree and checked against its Git blob. No dirty working-tree file was copied.

The packaged GLB is still the completed reference package from official commit `653a885435d95a19f341a80a89be1e9fbeccf1f1`: 12,552,372 bytes, SHA-256 `70b93bfdfb10c65662f6d37857e4a6a403dfb6ad950a9220acf91cb4145ab145`, Git blob `0706c119b99021b81696153985b0a1b53e375c3e`. The newer owner head-correction candidate at the pin is explicitly **WIP / UNVERIFIED and not packaged**. Its artwork/script are not imported. This integration does not claim that the later confirmed head correction is already in the current GLB. #526 avatar-alive WIP is entirely excluded.

Imported together: GLB, NOTICE, runtime hash pin, provenance JUnit test, exact source portrait and its provenance, builder, independent verifier, both render scripts, pose exporter, and their original README. All 12 file identities pass. Existing mesh license and MakeHuman provenance remain available; generated-texture provenance is separate and does not assert CC0 for generated pixels.

Actual integration checks in `asset-checks.json`: bytes/hash/NOTICE agreement PASS; source-portrait and builder hashes PASS; embedded atlas hash PASS; GLB structure PASS; existing independent geometry comparison PASS for 57,000 oriented triangles, all 21 morph targets, normals, skin influences, node/rig transforms and inverse bind matrices. Maximum protected scalar difference is zero across every primitive, with UV seam vertex duplication permitted. The runtime contract differs from baseline solely in `ASSET_SHA256`.

Caller audit confirms the current Activity creates `AvatarDirector` and `ContinuumHologramView`; the view loads the packaged path, enforces its SHA-256, runs `AvatarGlbContract.validate`, and creates `FilamentAvatarRenderer`, which uses `AvatarMorphMapper`. No caller, renderer, fallback, lifecycle or motion source changed. Host numerical preservation does not certify visual likeness or anatomy. The inherited independent rig-window failure stays open. Native Filament/Fold7, audio synchronization, full rebuild, render replay, Android JUnit and lint remain NOT_RUN here; parent runs shared Android gates.

## Reproduction and historical evidence

The original source README is `tools/avatar-3d/reference-atlas/README.md`. All build/verification/render/export programs and its committed portrait input are included. The baseline GLB is obtained from official commit `28b228698aef8c52e46c72b71fdd5e1915d6e76c`, as the README specifies. The integration-base GLB was independently checked to have the same SHA-256. The separate hinge validator, legacy GLB and mesh provenance are already in the baseline repository. Existing local JVM/Blender dependencies are required by the documented full reproduction; none were downloaded or executed for this import.

Historical evidence referenced by that verbatim README is retained upstream rather than copied. For its optional archived-pose replay and rejected-V1 reproduction, retrieve the exact linked original; alternatively use its Fresh source export section to create a new pose file and pass that file's measured hash to the renderer. Earlier render/test claims in the original README are source history, not integration results.

- Original handoff: https://github.com/Ullmann27/device-ai/blob/2350866904cb82484148c0c8c63416fa51034269/docs/research/omega/REFERENCE-AVATAR-V2-HANDOFF-20261104.md
- Historical evidence index: https://github.com/Ullmann27/device-ai/blob/2350866904cb82484148c0c8c63416fa51034269/docs/evidence/reference-avatar-v2-20261104/README.md
- Archived pose input: https://github.com/Ullmann27/device-ai/blob/2350866904cb82484148c0c8c63416fa51034269/docs/evidence/reference-avatar-v2-20261104/motion/final-runtime/actual-mapper-poses.json
- Rejected V1 input: https://github.com/Ullmann27/device-ai/blob/2350866904cb82484148c0c8c63416fa51034269/docs/evidence/reference-avatar-v2-20261104/geometry/rejected-v1.glb.gz
- Unpackaged correction status: https://github.com/Ullmann27/device-ai/blob/2350866904cb82484148c0c8c63416fa51034269/docs/research/omega/REFERENCE-AVATAR-HEAD-CORRECTION-20261104.md

Avatar rollback: revert its bounded integration commit as a unit, including GLB/runtime pin/NOTICE. No Activity or data migration is required.
