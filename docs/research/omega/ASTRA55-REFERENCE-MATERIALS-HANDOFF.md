# Reference cockpit: unified material port

Source checkpoint: `74e80a8151`, based on
`5348087805fa4ab4152ea04e9f20bb41afae9d95`. Integration target:
`work/astra55-reference-correction-20261112`. The coordinator owns integration,
publication, the side-root attachment, and the shared room transform.

Status: material/geometry source connected and host geometry checks `PASS`.
The unified 8-sample Fold/phone resting drafts at source `4b5663b8` were inspected
for desk, keyboard, and stand contact. No further material correction is needed
from those images. No device, frame-rate, or APK claim.

## Changes

The existing procedural generator now writes only
`tools/cockpit-workstation/source/pr801-workstation.glb` and its metadata.
It cannot overwrite the packaged unified scene. The source remains below
32 KiB. Its round desk now has a broader curved contour and actual edge bevels;
the keyboard tilts 12 degrees on two supports, with clearer keycaps.

`build_unified_assets.py` imports this seed without replacing its materials or
adding the old duplicate keys and monitor inserts. It adds the existing opaque
`monitor-live-pixels` quad, preserving bounds, UV orientation, and the existing
live texture pipeline. `--workstation-only` preserves the current orb bytes.
No engine, animator, shader, Kotlin renderer, avatar, or orb changes belong to
this material patch.

The source-bound draft at `6c951` established aligned native side panels and
visible stands. It also showed a very thin keyboard and a box-like desk front;
the revised geometry addresses those specific findings. That draft predates
the unified scene and is not a final screenshot.

## Coordinate contract

- Roots: `cockpit-room`, `cockpit-monitor`, `cockpit-side-monitors`.
- Native side rectangles, relative to the stage: `[.02,.57,.22,.87]` and
  `[.78,.57,.98,.87]`. Front opening plane: local `z=0`; bevels remain outside.
- Fold main monitor: `[.25,.54,.75,.89]`; phone: `[.02,.54,.98,.89]`.
- Hide the side root when `monitorWidth / width > .70f`.
- Desktop glass top: `-.430 * stageHeight` in the shared room coordinates.
- Main content bounds remain `[-.465,-.445,.032,.465,.38,.032]`.
- The canonical `monitorTransform()` remains unchanged. Room and side root
  use its common resting depth and compensation. This preserves native side
  positions and relative stand/table contact after perspective projection.
- The room contains the desk and keyboard, without a wall over the avatar.

## Reproduction

```sh
python tools/cockpit-workstation/build_scene.py
python tools/cockpit-workstation/verify_scene.py
blender -b -t 2 --python tools/cockpit-workstation/build_unified_assets.py -- --workstation-only
python tools/cockpit-workstation/verify_unified_assets.py --write-manifest
python tools/cockpit-workstation/verify_unified_assets.py
git diff --check
```

Checks cover binary/accessor bounds, finite unit normals, outward nondegenerate
seed triangles, all native apertures, three roots, material/alpha preservation,
the live plane and top-left UVs, seed budget, and runtime manifest hashes.
Repeated seed generation also leaves the packaged unified asset unchanged.
Two Blender builds produced identical workstation bytes. Reports:
`docs/evidence/cockpit-reference-materials-20261112/`.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Procedural seed | 30,776 | `f1edabf082ee4d30e275a862a375ac73dea65ab4e1f140ddb021845c87e6bcc3` |
| Packaged workstation | 48,728 | `03b6364e2ff396fd9207b129e765ce5d4a93975113315cc3896844e53fd8ad30` |
| Preserved unified orb | 1,425,616 | `ba4bd673e2196fcd8d970b3c83531ea26e20047ae530f092d0ef122808d6e161` |

Blender re-export duplicates some accessor data and appends the live plane.
The 32 KiB limit applies to the procedural seed; the canonical runtime already
checks the packaged workstation against its 2,000,000-byte limit. No limit was
changed here. Packaged geometry: 36 nodes, 15 meshes, 1,192 unique vertices,
1,022 unique triangles. The original avatar remains
`c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.

## Boundaries and rollback

Visual evidence: render checkpoint `39bd432bd1`,
`docs/evidence/cockpit-unified-draft-20261112/fold-idle.png` and `phone-idle.png`.
The curved desk and separate top/front edges are visible; the keyboard reads as
a tilted key surface on both formats. Main and side stands visibly contact the
desk, without evident floating or intersection. Native contents remain clear.
Smoked glass is subtle and its perimeter remains readable. Sampling noise did
not prompt material changes. This review covers those resting host views only.

Next: final sampling and focus-view inspection on the coordinator's current
source. Device rendering/performance remains unverified. The ordinary focus
lift can lower the foot roughly 1.6 pixels relative to the resting desk;
judge visibility in the actual focus render.

EXP-05/06 interfaces are unchanged; EXP-11 retains the existing integration
owner. EXP-12 rollback restores seed, generators, packaged workstation, and
manifests together. It does not restore the obsolete separate scene or replace
the original avatar/orb. Signing, installation, and main merges remain outside
this patch.
