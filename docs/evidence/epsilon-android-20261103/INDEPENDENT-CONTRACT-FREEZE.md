# EPSILON B independent geometric acceptance, frozen before A outputs

2026-11-03. No A mesh, render, measured outcomes or implementation inspected. Schema-only exchange permitted. This is independently authored validation; Android mapping tests are a separate suite. Test changes after viewing outputs must be recorded as post-freeze amendments.

Schema: glTF2 binary GLB, embedded buffer, metre units, Y up, +Z face/front, +X viewer right. Anatomical Left = +X. Unique named AvatarRoot > Neck > Head, Head descendants EyeLeft/EyeRight/Jaw. Face skinned to Neck/Head; rigid eyeballs. Rest rotations identity; positively oriented transforms. Face primitive targetNames order-independent but unique and complete: SIL PP FF TH DD KK CH SS NN RR AA E I O U BlinkLeft BlinkRight BrowUp BrowDown Smile JawOpen. Neutral = all zero. JawOpen deforms exterior skin; Jaw rigid node only inner-mouth avoids double skin rotation.

Frozen rejection criteria:
- Wrong GLB magic/version/length, chunks out of bounds, no JSON/BIN, external URI, duplicate required names, absent required nodes/targets, non-finite values, invalid accessor/index/skin references, invalid stride/count/bounds, repeated morph indices, incompatible primitives.
- Every target must contain actual finite nonzero POSITION deltas, count equals base positions. Max delta <= 0.2 * neutral face bounding-box diagonal. All rest scales positive; neutral and each full target have finite non-degenerate global bounds; mesh triangle indices valid.
- EyeLeft pivot X > EyeRight pivot X; eye pivots above jaw and separated 0.01..0.15m. BlinkLeft mean delta on anatomical left side > 3x opposite-side mean (and inverse for BlinkRight), allowing zero opposite side. Blink deformation local to upper half of face; brow deformation local upper half, mouth visemes local lower half (localized area evaluated from neutral face centroid, excludes a small 5% midline tolerance).
- Neutral zero weights; independently test each of 21 targets and simultaneous combinations JawOpen+AA, JawOpen+PP, JawOpen+O, Smile+BlinkLeft+BlinkRight, BlinkLeft without BlinkRight, opposite gaze directions. Zero target must restore exact neutral coordinates. Mirror anatomy reports objective sign, never a likeness pass.
- Rig hierarchy cycle rejection, skin influences finite in 0..1 with sums ~1 (tolerance 0.005), joint bounds valid. Head rotation moves face+eyes together; eye rotation must leave head skin unchanged; jaw rotation must not rotate skull. Geometric tests do not certify aesthetic naturalness, exact phonetics or identity.

Budgets: GLB <= 16MiB, <= 100k vertices, <= 200k triangles, <= 64 nodes, <= 32 targets, no remote texture/extension requirement. Runtime rejects unsupported sparse/accessor encoding rather than guessing.

Independent visual review after source/test freeze: neutral + each viseme + asymmetric blink + +/- gaze + head yaw/pitch, then speech combinations. Lip seal/teeth clipping, eyelid coverage and face identity remain explicit human/device review gates even when numerical checks pass.
