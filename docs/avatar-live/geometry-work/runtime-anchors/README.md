# Exact runtime GLB visual proof

Model SHA256 `f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e`. Blender4.3.2 glTF importer read the actual runtime bytes after deleting all source meshes/rigs. Renderer script `tools/avatar-3d/render_runtime_anchors.py`, commit81b22f54. No geometry was edited in this proof.

[30-anchor contact sheet](semantic-contact-sheet.png) · [Stepped turntable/pose MP4](runtime-proof.mp4) · [Exact controls and file hashes](runtime-anchor-manifest.json).

31 semantic images cover the30 requested anchors:20 has separate up/down images.12 additional frames form the orbit. Every image is512×512 with16samples, CPU2/nice10; this is **not high resolution**. The video displays actual rendered poses and12 orbit viewpoints; it is **not smooth live animation**, TTS synchronization, or measured phone FPS. ResearchGLB authored clips are separately preserved but not visually accepted.

**Appearance NOT_ACCEPTED.** Sparse/angular wires, opaque dark face/weak contour, eye/brow/lip proportions and likeness still differ from the binding portrait. Curiosity/focus/attention are bounded control approximations; broader smile scales the existing Smile target, with no independent cheek-smile target. Landmark annotations use actual control pivots and surface vertices nearest authored estimates; no biometric inference.

Anchor24 is an adverse independent-channel stress pose: AA1, JawOpen0, other morphs0, innerJaw12.6°, head/eyes0. The lower dental geometry protrudes below the lip. It is **not a faithful runtime mapper replay**: runtime combines AA with JawOpenq and Jaw12q°. Keep this stresscase visible; B is checking reachable combinations separately. Do not infer a reachable Android defect or collision-free articulation from this sheet alone.

Geometry/rig acceptance is separate: frozen independent checker771aec34 and real Android metadata preflight passed exactf39 bytes, evidence in B commit7caef9b6 / PR405. No owner identity approval, phonetic acceptance, device GPU proof, release, signing or installation.
