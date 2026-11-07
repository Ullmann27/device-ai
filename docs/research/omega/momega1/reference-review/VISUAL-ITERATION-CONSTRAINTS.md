# Independent visual iteration constraints

The accepted 26 motion/gaze contracts remain unchanged. This document makes the
remaining appearance and sequence checks concrete; it does not redefine a failed
appearance as success. Existing app layout, typography, controls and palette stay
authoritative. Reference media and reviewer contact sheets remain outside Git.

## Front comparison and next bounded variants

The reviewer extracted original primary-video frames at 0.5, 1.0, 2.5 and 4.5
seconds. The 0.5-second frame shows closed lids and an unobscured quiet mouth; the
1.0-second frame shows open eyes but a bright effect partly obscures the mouth;
the 2.5-second frame shows a rounded speaking mouth. They are visual references,
not proof of audio timing. Exact extraction hashes are recorded separately.

A reviewer-owned front contact aligns the reference, baseline `f39a89fc` and
surface candidate `fe76e85d` to the same crown-to-chin height, with uniform scaling
and no color, exposure or contrast adjustment. Manual crops are disclosed. The
rough ear-width/head-height ratio is about 0.76 in this reference versus 0.84 in
the asset. This is a manual projected-image observation, not recovered 3D anatomy
or an exact acceptance threshold; perspective, glow and pose differ.

The first diagnostic contact used an asset crop ending above the full chin and
overestimated this difference (about 0.91). Coordinate inspection corrected its
lower boundary from y365 to y392. Receipt version 2 records the replacement and
the superseded contact hash. No mesh change is justified by the earlier ratio.

Actionable differences, in priority order:

1. **Eyes and lids:** retain a readable dark pupil, suppress white iris emission,
   and reduce the round exposed-eye opening toward a relaxed almond shape. A new
   neutral lid position must still reach the original complete blink endpoint;
   full blink must hide the iris without opening a skin crack. Check front and
   slight yaw, including the actual observed-gaze range.
2. **Surface flow:** reference strands curve around eyes, nose, lips and toward
   the crown. The surface candidate's straight cylindrical horizontal/diagonal
   grid reads as a cage. Increasing grid density alone does not solve this.
   A bounded texture derived from real facial topology is a testable next variant;
   it must stay attached through blinks and jaw deformation without visible seams.
3. **Volume and light:** preserve readable front facial volume and emphasize soft
   contours/rim light. Holographic appearance does not require seeing the rear
   skull or inner eyeballs through the front face. Avoid competing doubled wire
   layers and loss of the nose/lip silhouette. Do not alter app-wide colors.
4. **Silhouette:** after evaluating lids/materials, test any narrower cheek/ear or
   chin-taper proposal as a separate geometry variant. Preserve real depth and
   independently check all joints, skins and morphs. A reference front view does
   not establish a particular unseen profile; profile review checks coherence.
5. **Mouth:** compare quiet lips against the unobscured reference frame, then use
   actual reachable AA/O/PP/I/U and interrupted-speech poses. Teeth must remain
   inside the mouth; darkness cannot conceal clipping. Do not judge a candidate
   from a stress combination the runtime cannot produce.

Compare variants using identical camera, lighting, resolution, exposure, seed and
sampling. Show the baseline too. More render samples reduce grain, not geometry
or material errors. A host-render improvement is still separate from the actual
Filament appearance, device cost and the owner's final visual acceptance.

## Actual reachable-pose export

`ExportReachableMapperPoses.kt` compiles and runs the actual production reducer,
director and mapper from exact source `26555aa6c59fdd4dd2e190defcdf49b983305555`.
It does not reproduce their equations in a second implementation. The generated
`reachable-poses-2655.json` contains 260 records:

- 224 idle frames at 24 Hz, covering two complete blink cycles.
- Ten exact idle/blink anchors, including the full closure at 4.51 seconds.
- Six samples across the existing 460-second clock wrap.
- Six existing Director task-state poses and three observed-look poses.
- Five speech visemes reached through actual reducer energy smoothing, each
  followed by an interrupted/stopped pose.
- One reduced-motion pose.

Inputs are explicitly synthetic contract signals. This is neither real camera
tracking nor speech/audio/model execution. The exporter records the production
joint angles and morph weights; the renderer must replay those values on the
actual GLB at a **fixed camera**. Camera-angle inspection remains useful for
geometry, but a camera orbit cannot substitute for head animation.

## Minimum independent sequence inspection

Before a naturalness claim, provide a fixed-camera sequence using all 224 idle
records at their actual 24 Hz spacing, or a clearly disclosed equivalent direct
runtime capture. Low-resolution preview is acceptable for initial motion review,
but not final material fidelity. Inspect the complete sequence and the full-blink
anchor, including eyelid closure/reopening, stable pupil placement, subtle coupled
head/eye movement, visible volume, absence of texture sliding, and absence of
mechanical discontinuities or invented mouth motion. Report the exact renderer,
source/asset/export hashes, frame count and any interpolation.

Also inspect actual AA/O and stopped poses, reduced motion and clock wrap. Reusing
one static frame, moving only the camera, or manufacturing mouth motion from an
idle state cannot pass. A sequence with synthetic speech events can validate the
geometry contract; it cannot pass real lip sync or voice identity.
