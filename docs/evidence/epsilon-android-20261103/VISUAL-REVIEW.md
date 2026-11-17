# Independent B visual review — runtime f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e

Actual runtime GLB imported independently into Blender4.3.2. CPU8-sample neutral renders use the exact Kotlin preflight focus and explicitly verified orthographic vertical planes. Phone texture279×390 comes from ZETA HERO388×390; Fold587×470 from HERO816×470. Images are rendered at2× pixels for inspection. These are host geometry/framing renders; lighting and rendering are not Android Filament pixel evidence.

Framing: PASS for inspected neutral phone/Fold. Full-bust fit makes the face small because shoulder width exceeds height. Adopted head/upperneck framing visibly preserves full skull and both ears, explicitly cropping outer/lower shoulders. No mesh vertices are removed. Runtime also keeps the original orb in the separate right28% region of the same hero/clock.

Visual match: NOT_ACCEPTED. Compared to the binding portrait `docs/avatar-live/reference-work/jarvis-neutral-frontal-generated.png` (A SHAa850c508…), current render has sparse angular wires instead of dense flowing filaments, a much darker opaque facial surface and weaker luminous contour, paler eye whites/more severe neutral brow appearance, and narrower closed lips. Lighting contributes to brightness differences; the density/shape/material differences still prevent a reference-identity or finished-art claim.

Geometry acceptance is separate: unchanged frozen challenger passes v2 structural/deformation/mirror/rig checks. Original candidate failures are retained. New candidate expression screenshots must be tied to f39a89fc… or its exact authored source63b42c…; earlier mixed-generation A renders are not accepted. Blink/smile/speech visual naturalness and actual Android skin/eye/jaw behavior remain pending independent visual/device review.

Device gates: Filament GPU rendering, context/surface loss, Fold7 timing, concurrent local-model frame time/RAM/thermal and accessibility/device screenshot review NOT_RUN. No sign/install/release.
