# Cinema 2 continuation — WIP

## Source and build

The owner explicitly authorized the proven GitHub Actions Code53 QA channel on
2026-11-11. Workflow `continuum-code53-frozen-qa.yml` was carried from #746,
pinning runtime source `43b8d9f4f7bfb7bef9714ba28efde5bb9622d482` and harness
`67bf048a7d50a78b027da27a6b9814cbd2457372`. Runtime tree is checked before QA.
Run: https://github.com/Ullmann27/device-ai/actions/runs/42966389910
This checkpoint does not claim that run has completed or tested later code.

## Connected work energy

Previously `HollywoodFuiStageOverlay` only sampled the construction transport;
its energy ended after construction. Its source was the tapped control or
window origin, not the actual orb.

`JarvisWorkWindowHost.plan` now carries the existing shell's orb bounds to the
existing overlay, including refits. `workStarted` / `showStatus` / `workEnded`
already supply real task activity. `JarvisFluidLight.workingSample` consumes
that activity and the existing window clock; no second bus, timer or executor.
STOP, result, closing, reduced motion and suspended/thermal budgets stop the
sustained band. Existing bounded construction/return effects remain.

19 isolated Kotlin/JUnit tests PASS (JarvisFluidLightTest and
JarvisRenderQualityTest). These are Kotlin 2.2.21 / JDK21 host tests, not a full
Android compile. A native Canvas regression checks actual corridor pixels,
orb refit and work cancellation; Android execution remains pending here.
The existing Java2D preview compiled successfully; it does not exercise this
Android overlay connection and is not claimed as its visual proof.

## Voice request

The connected ElevenLabs workspace was inspected without speech generation.
The current Android path is AgentActivity -> ApprovedSpeechChunkQueue ->
Android TextToSpeech, with utterance-bound avatar callbacks. It contains no
ElevenLabs runtime adapter or configured ElevenLabs voice ID. The intended
same voice cannot yet be identified from the app source/workspace.
The ChatGPT connector is not an Android runtime credential. No personal voice
identifier, account data, key or sample was added to the public repository.
Voice selection and a secure runtime credential path remain unresolved;
existing offline speech and internet restrictions are preserved.

## Existing glass depth made drawable — Android QA pending

GlassPanel previously called JarvisReferenceHud.surface inside its own
clipToOutline boundary. That surface already draws bottom/right thickness,
rear glass and contact shadow outside its front rectangle. The child outline
cut those existing pixels off. JarvisWorkWindow now draws that same surface
once from its protected parent canvas, using the panel's actual native matrix
and opacity. Child content remains clipped; face/orb/dialog exclusions remain.
No new material, renderer, clock, asset or hitbox was introduced. A native
Canvas regression checks the exposed lower glass strip and protected face.
This new Android change is WIP until its source-bound hosted run completes.

## Remaining acceptance

Full Android and native Canvas tests for the energy patch, identical before /
after sequences, further depth/glass improvements, complete connection audit,
new unsigned APK and Fold7 hardware acceptance remain open. No original assets,
experimental model weights, signing, main, installation or release changed.
Rollback: revert the scoped flow patch; the previous construction path remains
in JarvisFluidLight.sample. Compatible with EXP-05/11/12; no research promoted.
