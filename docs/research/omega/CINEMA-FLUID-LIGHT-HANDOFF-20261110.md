# Cinema fluid-light integration — 2026-11-10

## Scope and ownership

Base: PR #706 / `17491f4d8103f0e55836369333fab07e80e6e9c2`, rechecked live before implementation and publication.
Canonical target: `cinema/fluid-light-stage-20261110`, PR #715; current Cinema integrator owns the combined source.
Original release, main, package, signature, assets and model weights remain unchanged. No signing or APK installation is authorized by this work.

The owner requested visual checkpoints before further design expansion. The first Java2D frame and cover frame were shown. Subsequent work is correctness, verification and reproducible previews; further artistic direction awaits owner feedback.

## Actual existing paths and changes

- Existing command/ActionStarted/DocumentQuestion → Airbus orchestrator → JarvisShellView still owns scene events.
- JarvisCinemaEntrance → ShellRenderer now includes bounded batched light transport and GlassBackend.drawFluidEdge.
- The actual JarvisWorkWindowHost → HollywoodFuiStageOverlay receives the same fluid pass, existing construction clock and render budget. The feature is not confined to host preview.
- RuntimeShader: rounded contour mask, downward noise filaments, four disjoint perimeter strips, reused Paint/shader, no new full-screen offscreen pass; failure returns to the portable edge painter.
- Native yaw no longer also applies a flat 2D rotation. Foreground module panels share the sampled CinemaFrame. Idle alpha=1 avoids new saveLayer passes.
- Interrupted reconstruction preserves its actual pose before fading and rebuilding. Rapid-turn/result lifecycle regressions pass the targeted Android/Robolectric checks.
- Projected window light follows actual X/Y, atomically; turn-scoped gaze IDs prevent old close events affecting new windows. Original GLB hash/21 morphs verified unchanged; see separate avatar handoff.
- RenderBudget controls animation, two/four strands and glow. No independent timers, fake GPU telemetry, temperature claims or new cloud transmission.

## Reference review

Private owner recording was downloaded only for local inspection, never committed: 466.602656s, 57,474,230 bytes, H.264 532×1000, one video stream, no audio stream. Inspected the overview and work-window frames; source footage shows the existing head/orb and local-thinking window, plus thermal refusal text. This does not establish actual CPU core temperature, audible TTS quality or the correctness of unobserved actions.

Earlier owner reference directory was located through the existing project dossier. One generated reference clip was viewed as a contact sequence: spatial side panels, camera motion, app/document projections and moving light connections. This is artistic reference, not existing app behavior. Neither private video nor its frames are published here.

## Verification boundaries

- Initial exact candidate host law: 31/31 Kotlin/JUnit PASS (27 existing +4 fluid/abort tests).
- Avatar: two real failing regression probes on base, 19/19 final host tests; see committed avatar logs.
- Historical targeted Node contracts: 18/18 PASS at `e2fb22d`, per `qa-receipt.json`; not a claim about later source.
- PNG-only production-painter preview: 32 PNG stills and 0 video frames were reported, but its later helper/source receipt is missing (`SOURCE_UNBOUND`). The original input hashes bind only the earlier preview, not this check or a device result.
- Embedded shader: 8/8 actual Skia RuntimeEffect software-raster tests, including compilation, premultiplication, transparent content area, deterministic replay and disjoint-strip equality.
- Targeted Android SDK36 compilation of native painter/glass/overlay paths: PASS, no GPU execution.
- Complete Gradle app compile (`:app:compileDebugKotlin`): PASS.
- Targeted Gradle tests: 56/56 PASS, 0 failed/skipped, including 3 Robolectric lifecycle tests. Host entrance/fluid tests: 33/33 PASS.
- Configured Android `NewApi` lint: PASS, 0 issues, standalone retry BUILD SUCCESSFUL in 2m10s. This is the project-configured API compatibility check, not a complete accessibility/style audit. Earlier combined run lost its daemon after all 56 tests completed; moving toolchains off RAM-backed /tmp and bounding lint heaps allowed the isolated check to finish.
- GPU / Filament image comparison / emulator / Fold7 / device thermal/CPU/GPU/frame-time measurements: NOT_RUN.

Java2D screenshots use the real shell/orb painter with a clearly labelled portable avatar stand-in and synthetic empty scene labels. They do not show the original Filament GLB or AGSL. No private chats, documents or fabricated live metrics are drawn. Shader software images are a distinct proof. No host duration is marketed as Fold7 FPS.

## Reproduction

Use `scripts/cinema-fluid-light-preview.sh OUT LABEL [stills|video]` with existing `KOTLINC`, `ANDROID_JAR` and Java21. For baseline set CONTINUUM_PREVIEW_SOURCE_ROOT to a checkout of 17491f4d. OUT must be outside the repository. The helper compiles the production painters and exact extracted tile/STOP members, records every source hash, and generates Fold884×760 / Cover411×960 stills by default. Optional `video` mode produces 6s/24fps encoded previews. The owner requested still images because video playback failed; deliver PNG images directly. Encoding rate is not a measured device frame rate.

Run `scripts/continuum-fluid-edge-shader-test.py` with host-only skia-python installed. This adds no app dependency.

Relevant Node commands and host logs are under docs/evidence/cinema-fluid-light-20261110. Android full-suite and device tests must be repeated on the final proposed release SHA before any signing request.

## Research and integration decision

Independent PR #716 / `00cf15d925d2c114d8ce57ee23dac473a89e6e0f` fixes explicit contrast/negation in existing opt-in CTX3 shadow resolution. 106/106 real Kotlin/JUnit checks independently repeated. Seven-day plan, voice/context call-path audit and promotion criteria are in that branch. Do not describe this as improved live Gemma answers. Keep separate per owner instruction until independent source-bound acceptance; no automatic promotion or new model training.

## Compatibility, rollback and remaining work

Existing EXP-05/06/07/12 and Cinema EXP-15 thermal policy remain the contracts. No new event bus, database, avatar, package or signing route.
Rollback is revert of this branch's commits only; keep source and research branches open.

PORTIERT: actual shell and actual work-window/Filament call paths changed.
VERIFIZIERT: only the specific host/SDK/software checks explicitly listed.
GERÄTEBESTÄTIGT: NOT_RUN.

Remaining: owner visual direction; full Android regression suite, original-head GPU capture and live STOP/new-question sequences; actual Fold7 frame-time/memory/thermal measurements; performance tuning based on those measurements. No finish/release claim.
