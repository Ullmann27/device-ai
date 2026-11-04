# Current asset: separate hinge and fixed-region verification

The existing V2 validator passes on the currently packaged asset. No GLB,
validator, threshold, rig hierarchy or asset pin was changed in this round.
The original asset required by the checker was already present in the repository
as `assets/avatar/jarvis-runtime-v1.glb`; its exact SHA-256 matches the checker's
fixed original-source pin. No reconstructed or substituted original was used.

## Exact inputs and outcomes

| Input or gate | Result |
| --- | --- |
| Original runtime GLB | `f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e` |
| Currently packaged GLB, 10,165,644 bytes | `5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3` |
| Unchanged V1 validator | `4550fb6deaa92d92401f482baad1dff80636c71d6e516cb46a7dc4fdc8fc445e` |
| Original under V1 | PASS |
| Actual current GLB under V1 | FAIL, `rig_hierarchy_orientation` |
| Actual current GLB under existing V2 | `PASS_SEPARATE_HINGE_AND_FIXED_REGION_V2` |
| Existing V2 adversarial suite on the current GLB | 16 tests, OK, exit 0 |

The rig helper first reproduced these asset outcomes on the unchanged starting
tree. Its raw receipts and byte-equality check are in `rig-helper/`. The
coordinator then executed the existing V2 CLI and its 16 adversarial self-tests
on the integrated source. Commands, stdout, stderr and source hashes are in
`hinge-v2-final/`. The root receipt records local commit
`dccd0901b2f300fde268d35e533d18b79664f20e`; the subsequent final source changes
only one Kotlin test file. All five GLB/validator/self-test inputs are verified
byte-identical in `source-identity.json`.

## Why V1 and V2 have different outcomes

The original exporter used a manually placed Jaw point at world Y=0.227 m.
V1 simultaneously treated this point as the joint and as the landmark for its
fixed mouth-region checks. It requires jaw Y within 0.20..0.25 m and an eye-to-jaw
Y separation within 0.04..0.09 m. The current functional hinge is higher, with
compensating child transforms. It therefore violates those old windows.

V2 already separates the actual hinge from the fixed-region landmark. Before
creating any diagnostic coordinate view, it verifies the current hierarchy,
hinge basis, lack of exterior Jaw skinning, neutral child/control transforms,
actual oral deformation support and actual tooth geometry. The unchanged V1
validator then runs on an explicitly labeled diagnostic coordinate view that
retains the candidate's mesh data and compensates the changed coordinate origin.

| Measurement on the actual current GLB | Value |
| --- | ---: |
| Hinge world Y | 0.26699999809265135 m |
| Maximum Y of the active oral deformation support | 0.25978612899780273 m |
| Left and right eye world Y | 0.2884144186973572 m |
| Eye-to-hinge Y separation | 0.02141442060470583 m |
| Hinge world Z | 0.03499999921768904 m |
| Minimum Z among actual tooth vertices | 0.1366769978776574 m |
| Maximum neutral child/control world-matrix deviation | 2.7755575615628914e-17 |
| Maximum diagnostic neutral world-matrix deviation | 2.7755575615628914e-17 |

The functional hinge is above the active oral support, below the eyes and behind
the actual teeth. The controlled diagnostic view passes V1. This does not change
the actual asset's V1 FAIL into a PASS: its early failure and the separately
successful V2 result are both retained in the same receipt.

## Counterexamples and limits

The 16 existing self-tests accept the actual candidate and reject invalid low,
high, forward or lateral hinges, missing child compensation, translated tooth
controls, hierarchy changes, nonfinite coordinates, exterior
Jaw skinning, changed eye positions, mouth deformation in the upper face, blink
deformation in the lower face and altered original-source bytes. They also retain
the previous invalid V1 control family. Raw per-test results remain available.

These checks establish the stated numerical and functional stylized-rig
conditions. They do not certify human temporomandibular anatomy, pose
intersections, reference likeness, visible native-GPU output, a physical device
or audible speech. No historical receipt from the different `6b199...` asset is
used as acceptance of the current `5f49...` asset.
