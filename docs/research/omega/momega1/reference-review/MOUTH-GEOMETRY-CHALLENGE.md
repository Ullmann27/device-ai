# Independent reachable-mouth geometry challenge

The fixed-camera replay exposed teeth outside the displayed O-shaped lip opening.
The same visible behavior is present in the unchanged original asset, so the
new procedural texture is not its origin. This document separates observation,
geometric evidence and proposed explanations before a repair is supplied.

## Observed and independently calculated

Original asset: `f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e`.
Poses come from the actual production mapper/reducer export at `26555aa6`, not
the older stress anchor that combined unrelated controls. The reviewed full AA,
PP, O, I and U poses each have JawOpen=1 and jawPitch=12 degrees. Inputs are
synthetic, but those outputs are reachable through the unchanged reducer.

The reviewer wrote a separate canonical glTF reader, morph/skin evaluator and
rigid-joint transformation path. It does not import the author's Blender code.
It applies the same specified `restLocal * Ry * Rx` transformation that the
production Filament path applies. For these speech cases head/eye angles are zero,
making the jaw comparison especially direct.

An initial depth projection overcounted ordinary teeth visible *inside* the mouth
opening. Those projection counts are explicitly not penetration claims. The
refined check instead finds strict noncoplanar tooth-edge/face-triangle crossings,
then retains only crossings on the frontmost projected face surface. Hidden
internal contacts are separate. Crossing witnesses include both edge endpoints,
the triangle's three points, segment fraction and barycentric coordinates.
Independent reconstruction agreed to below `4e-17 m` in the recorded witnesses.

Observed frontmost crossing counts in the original asset:

| Pose | Count | Affected lower teeth |
|---|---:|---|
| Neutral | 0 | None |
| AA | 16 | 1, 6 |
| PP | 152 | 1, 2, 3, 4, 5, 6 |
| O | 65 | 0, 1, 6, 7 |
| I | 36 | 1, 6 |
| U | 28 | 0, 7 |

These are sampled crossings, not counts of independent defects, full collision
certification, native-render execution or device evidence. They corroborate the
observed mouth artifact without assuming that a bright pixel alone is penetration.

## Repair constraints and competing hypotheses

`BustSkin.joints` contains Root, Neck and Head, not Jaw. Thus jawPitch rotates the
rigid inner mouth/lower teeth; it does not add a third outer-skin jaw deformation.
AA and JawOpen may nevertheless overlap in their outer-face deltas. A component
ablation is appropriate before changing those semantics.

The current jaw pivot is low in the head. A higher hinge with neutrally preserved
child transforms is a separate possible explanation for the lower teeth moving
forward during opening. This is a hypothesis, not an approved repair. Check the
actual geometry and all sampled poses before preferring it over tooth placement,
viseme composition or a combination.

Do not darken/delete teeth, hide tests or alter the camera to conceal crossings.
A candidate must preserve a coherent quiet mouth and valid joint/morph topology,
remove the sampled frontmost crossings, and show the same actual poses under the
same camera/material conditions. Intermediate jaw/viseme combinations and profile
views remain additional checks; zero crossings in this finite sample alone is not
complete appearance acceptance.

`check-mouth-occlusion.py` now emits exit 1 when sampled frontmost crossings exist.
The first exploratory diagnostic returned exit 0 and recorded counts; it is not
retroactively described as an executable RED assertion. The assertion version
is frozen before the candidate geometry repair and must be executed on both
original and candidate assets. The previously accepted 26 motion/gaze tests remain
unchanged. Any justified mapper-semantic repair requires an explicit versioned
contract change and new independent functional tests, not silent weakening.
