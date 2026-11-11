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

## Maya general response voice — source integrated, Android QA pending

The later owner decision selects the existing public voice Maya – Warm Californian,
voice ID `ii0s2u4R3UFnxKL6DOrz`, for all normal Continuum answers. It supersedes
new-voice design. No owner clone, preview synthesis or sample audio is used.

AgentActivity -> existing ApprovedSpeechChunkQueue -> optional MayaSpeech provider
-> ElevenLabs PCM -> the existing utterance listener, TtsHologramSignal and avatar.
The local model, memory and sole reply queue remain unchanged. Only approved final
reply chunks leave the device after explicit opt-in and key entry in the app.

Long press TON -> Maya -> enter key -> Maya aktivieren. This stores a separate
Android Keystore AES-GCM encrypted token, with screenshot/autofill/state saving
blocked for the field. No generation on setup. Key deletion and offline selection
are reachable in the same picker. Global internet OFF/ASK never become ALWAYS_ON;
Maya only uses ALWAYS_ON with the turn's current central consent revision.
Other modes retain the offline voice and display a fallback notice. STOP, new
reply, pause, provider change, internet mode change and destruction cancel the
provider; delayed callbacks cannot resume an obsolete utterance. Network/audio
failure leaves the text intact, reports failure and does not retry billable calls.

The official API contract used is
https://elevenlabs.io/docs/api-reference/text-to-speech/stream
with multilingual_v2, the selected voice and PCM 24kHz mono. Each existing bounded
reply chunk is downloaded before playback; this is sequential chunk playback,
not low-latency incremental PCM streaming. PCM amplitude feeds the existing avatar;
no new phoneme alignment or voice clone is claimed. Maya is originally English:
standard-German pronunciation/naturalness remains a real listening acceptance item.

11 isolated JVM tests PASS: provider lifecycle/permission/error races and existing
long-answer chunking. Provider + Android audio + secure-token classes compile
against Android API36. Full AgentActivity compilation and Robolectric settings
regressions are pending hosted QA, not covered by that partial compile.
Fold7 playback, key persistence, live ElevenLabs response and avatar lip-sync:
NOT_RUN. No account key, credits or paid generation were used for development.

Compatibility: existing voice/EXP-05/12 contracts remain; no experimental model or
learning switch changes. Rollback: choose Android Offline in the picker or revert
this voice patch. Existing offline voice preferences and original assets remain.

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
