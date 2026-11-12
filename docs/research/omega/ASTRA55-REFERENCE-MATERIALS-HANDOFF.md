# Reference cockpit: unified material port

Material geometry checkpoint: `74e80a8151`. Final builder integration is based on
`9e67f40eb4210d25ea764888d27ede555dc81327`, containing the original five-ring
orb from main `7d6479f8c7c1c8e3c59f62399fb0a366676afe53`. Integration target:
`work/astra55-reference-correction-20261112`. The coordinator owns integration,
publication, the side-root attachment, and the shared room transform.

Status: material/geometry source connected and host geometry checks `PASS`.
The unified 8-sample Fold/phone resting drafts at source `4b5663b8` were inspected
for desk, keyboard, and stand contact. No further material correction is needed
from those images. No device, frame-rate, or APK claim.

The earlier host images establish the material geometry only; they predate the
new original five-ring orb. The final combined five-ring scene requires its own
source-bound render.

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

The final rebind retains the new main `orb()` generator unchanged and preserves
its existing packaged bytes. It restores `--workstation-only`, keeps the side
monitors as independent scene roots, and omits the duplicate horizontal keycaps
from main. Native side contents already cover their screen regions; additional
inactive quads are unnecessary. Both the five-ring/original-geometry checks and
the main Filamat source/output/version checks remain in the combined verifier.

The rebind completed with `--workstation-only` after acquiring the shared build
lock. The workstation, seed, original five-ring orb, avatar, and Filamat remain
byte-identical before and after the build. The new build manifest is valid;
all combined checks passed. The original five-ring `orb()` function is also
AST-identical to main `7d6479f8c7`. Separate evidence records the build and hashes.

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
Two earlier Blender builds produced identical workstation bytes. Their reports
remain in `docs/evidence/cockpit-reference-materials-20261112/`; the final
five-ring rebind has separate reports in
`docs/evidence/cockpit-five-ring-materials-20261112/`.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Procedural seed | 30,776 | `f1edabf082ee4d30e275a862a375ac73dea65ab4e1f140ddb021845c87e6bcc3` |
| Packaged workstation | 48,728 | `03b6364e2ff396fd9207b129e765ce5d4a93975113315cc3896844e53fd8ad30` |
| Preserved original five-ring orb | 1,148,836 | `fdfe54ce9aa2ed6af5ec6866c1078a4a34ece511e66b29cd1b3672f83229079c` |

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
