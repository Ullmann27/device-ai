# Explicit mouth-composition contract version 2

V1's 26 passing motion/gaze checks remain unchanged and archived. One of them
asserted exact preservation of the old independent JawOpen/viseme weights. New
evidence shows that this preserved a shared anatomical opening twice: the pinned
original builder places `mouth-open=0.65` inside AA and `mouth-open=0.8` inside
JawOpen. Actual reachable combinations produce the separately reproduced mouth
geometry failure. Preserving that old arithmetic is therefore no longer a valid
semantic requirement for the repaired mapper.

Before inspecting a composition candidate, V2 freezes:

- The eleven unrelated mapper requirements unchanged.
- All fourteen existing observed-gaze requirements unchanged.
- The combined shared opening must not exceed the larger anatomical opening
  requested by the two individual drivers; the viseme-specific shape remains.
- Pure audio opening remains functional, and inner-jaw rotation agrees with the
  actually composed outer opening.

`MapperMotionV2ChallengeTest.kt` consequently has thirteen cases; together with
the unchanged observed-gaze class, V2 has 27 cases. This is an explicit version
change, not a skip or a hidden edit to V1. Both old and new behavior will be reported
against exact source versions. A V2 source-contract pass does not certify geometry,
phonetic correctness, audio synchronization or appearance.

An additional input family is frozen in `ExportMouthHoldoutPoses.kt`: every one
of the fourteen non-SIL visemes, audio callback levels 0/0.18/0.63/1 with three
real reducer updates, and cue weights 0.45/1. This produces 112 reachable speech
poses plus neutral. It executes the actual reducer and mapper; inputs are synthetic.
The original five max poses remain required and are not replaced by this family.

The geometry checker and visual comparisons must still pass independently. A
composition change cannot be accepted merely because weights satisfy the new
contract; teeth/lips must remain coherent under actual resulting poses. No
darkened/deleted geometry, changed camera, altered tests or unstated permission
change substitutes for that evidence.
