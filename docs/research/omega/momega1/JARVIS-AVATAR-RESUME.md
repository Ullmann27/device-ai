# Avatar continuation checkpoint — 3 November 2026

The requested reference appearance is **not finished or accepted**. Existing
Continuum layout, colors, fonts, buttons, orb/2D choices and owner preferences
remain unchanged. No Fold7, actual camera, real voice comparison, native GPU
measurement or device installation was performed.

## Concrete source and artifacts

- Complete narrow mouth/rig source: `62122c55077644de5bb1993347b7749c7e53610b`.
  Packaged asset: `6b199c5989640b516b8c45401ececeadb8ed1baae1d1e1ab5db20d19e966aa1e`.
  Shared opening is composed once; the actual inner jaw follows it. The hinge
  moves 40 mm with inversely rebased rigid children, preserving neutral geometry
  and all original mesh, morph and material bytes. Do not integrate the mapper
  without its paired rig.
- Last production change: `0996b42e4c8bdbc1a3af49b85c0a3382bbcac346`, only
  `FilamentAvatarRenderer.kt`. Existing View color/gamma processing is enabled;
  bloom is bounded to 128 pixels, four levels, LOW quality and strength 0.06.
  No new clock, engine, entity, lensflare or chromatic aberration. The existing
  `destroyView` path owns cleanup. Actual SDK36/Filament1.56 AAR API compile PASS;
  native visual/resource behavior remains NOT_RUN. Independent source review
  was requested from the separate PR #434 lane.
- [Complete 224-frame preview](../../../evidence/momega1-avatar-look/3805ba43/idle-preview.mp4)
  and [unretouched contact sheet](../../../evidence/momega1-avatar-look/3805ba43/idle-contact.png):
  actual mapper output, fixed camera, 24 fps, 9.333 seconds, two blink cycles,
  synthetic inputs. The 192×224, two-sample Cycles preview is noisy and is not a
  final look or naturalness acceptance. Both 112-frame source manifests and
  hash-checked encoding evidence are adjacent.
- Higher-quality same-condition material comparisons are preserved under
  `docs/evidence/momega1-avatar-look/{03caa24f,c016237a,6038b48a,3805ba43}/`.
  These are real CC0-derived GLB renders; private reference videos/frames are
  deliberately absent from the repository.

## Measured results and retained failures

At exact `62122c55`, author Kotlin/JUnit 38/38, Node 5/5 and Python checker-unit
tests 8/8 pass without skips. The actual packaged Kotlin GLB preflight passes.
The independent reviewer confirms V2 27/27 and the same packaged hash/preflight.
The canonical tooth-edge/front-face checker runs all six original maximum
poses and 113 additional real-reducer poses with zero visible intersections.
It also independently passes all 113 poses on the combined experimental look
asset `3805ba43261784993d3f4c65bb24df2a0ee306b89962167d81763c12c9d81deb`.

Two old contracts are retained as explicit RED, not hidden or weakened:

1. V1's exact doubled mouth mapping is obsolete after the proven common-basis
   correction. V1 remains archived and actually reports 25/26; independently
   frozen V2 reports 27/27. Author initial overly broad total-deformation RED
   and corrected pre-fix shared-basis RED are both preserved.
2. The original EPSILON validator treats the old chin-position pivot both as
   the jaw hinge and as a mouth-region landmark. It rejects the corrected hinge
   on its old metre bounds. V1 is unchanged; the independent reviewer is
   implementing a separately frozen V2 separating those meanings while keeping
   original skin/morph protections. Do not merely widen its range or claim all
   legacy geometry checks pass. Raw failure is in
   `docs/evidence/momega1-avatar-mouth/62122c55/geometry.txt`.

## Appearance findings and next bounded step

`03caa24f` restores readable human eyelids and facial volume. Broad contour
emission `c016237a` flattens the face and creates bright closed-lid rings; it is
rejected. Narrow emission `6038b48a` preserves volume but remains dim. HDR4 gives
a stronger edge but still differs substantially from the primary reference.
None of these look derivatives replaces the packaged app asset.

There is a concrete unresolved portability concern: pinned Filament1.56
`AssetLoader.cpp` multiplies the glTF emissive factor by extension strength,
also sets a separate strength parameter, and the Ubershader template multiplies
both. This suggests doubled strength in that path; it has not been measured
in a native render. Therefore the HDR-extension look must not be promoted from
Blender evidence. Source URLs and hashes are preserved with the native compile
receipt. No new SDK/Xvfb setup was started in the remaining session budget.

The next small experiment can use `derive-contour-emission.py --variant narrow
--topology-emission` with the default standard strength **1**, preserving the
density-normalized real triangle paths and avoiding the unverified extension.
The new optional topology-emission switch is saved but **not yet executed or
accepted**. Compare it using the same fixed camera/pose/lighting, then validate
the exact combined eye/material/rig asset independently. Any host bloom image
must be labeled separately from native Filament pixels. Reference-layout and
full-app screenshots belong to the separate readiness/layout lane.

## Reproduction and rollback

Original CC0 source remains recoverable from commit `26555aa6` at
`app/src/main/assets/avatar3d/continuum-avatar.glb` (SHA `f39a89fc…`). Derivation
order for the experimental clip is: topology surface → eye relaxation → narrow
HDR4 contour → neutral-preserving jaw hinge. Tools are under
`tools/epsilon-avatar-contract/`; JSON records identify every intermediate hash.
Actual mapper input/export is owned by independent PR #434. Render with its
source/pose hash environment variables, run two disjoint 112-frame batches, and
use `encode-idle-replay.py`, which refuses missing, duplicated or mismatched
frames. The clip SHA is
`be5841f5bb57f708efeb09eb0b2c368bb95ac48c7f3b5cd7e0f68266745d9bca`.

Rollback the separate color-pipeline commit if native testing rejects it.
Restore the paired mapper/rig together if rolling back the mouth correction.
No data migration, owner memory, model execution, signing or publication is
involved. Original 2D/orb fallback remains available.
