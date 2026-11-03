# Independent reference acceptance freeze

Baseline: `d8a927dd6ced4dea489e2042afb4b5624157fa3b`.
This document is frozen before a new visual candidate is supplied to this reviewer.
It is a review contract, not an implementation acceptance or device result.

## Authority and scope

The owner's latest clarification preserves **our existing agreed colors, fonts,
buttons and layout**. The orange-marked ten-second video supplies movement,
effects, speaking style, head behavior and working-animation inspiration adapted
to that design. Its changing camera shots, mobile mockup, panel layout and palette
do not replace the established application design. The later emphasis requires a
convincing humanoid head, including natural idle movement; a flat procedural face
with added decoration cannot fulfil the requested 3D appearance.

`design-baseline.json` hashes the current UI/theme/font sources and original 2D
renderer palette. A changed hash requires explicit source review; it is not
automatically an unauthorized change, because a necessary renderer connection may
also live there. No change may silently redesign navigation or disable the
existing orb/2D fallback. Other concurrent coding-entry changes are reviewed by
their own independent lane.

## Reference observations and limits

All five videos were located by Chief in the requested Drive folder, fetched as
original files and independently inspected using nine time-spanning keyframes
per video. `reference-metadata.json` binds the complete file hashes, durations and
stream metadata. Private media and frames stay outside Git. This initial sampling
is **not** a claim to have heard or watched every frame of the recordings.

| Index | Observed visual content | Use in acceptance |
|---|---|---|
| 0 | Translucent humanoid head; orbital light; changing eyes/mouth; document particles; network core; waveform; desktop/mobile shots | Primary motion/effect reference; preserve our design |
| 1 | Narrated dashboard/voice-assistant and delegated-work illustration | Secondary interaction inspiration; reported business results are unverified claims |
| 2 | Promotional model-provider routing diagrams | No authorization for cloud fallback or free-service claims |
| 3 | Long illustrated assistant installation/function walkthrough | Inspiration for guided setup; no proof of local Continuum functionality |
| 4 | Orange/red assistant display and virtual-worker scene | Secondary work visualization; no color/layout override and no proof of actual workers |

The videos contain audio streams, but the review environment cannot listen to
audio. Voice identity, timbre, pronunciation, speaking rhythm and audiovisual
synchronization therefore remain **NOT_RUN**. Stream metadata, waveform metrics
or source text cannot turn these into PASS. No paid analysis, new voice model,
cloud upload or external voice clone is authorized by this review.

## Frozen required checks

| ID | Criterion | Evidence needed for PASS |
|---|---|---|
| D01 | Existing design survives | Source comparison of protected design files; same-size before/after host renders; unchanged text/button/layout regions outside the intended avatar/effect region; no invented telemetry |
| D02 | Existing choices survive | Actual owner orb/2D selection, fallback and lifecycle contracts remain; no forced renderer, copy app or package change |
| H01 | Convincing humanoid 3D head | Actual 3D asset/runtime provenance plus rendered front, slight yaw/pitch and profile views with coherent eye sockets, nose, lips, jaw, depth and perspective; visual comparison against primary video; 2D path drawing cannot satisfy this row |
| H02 | Natural idle behavior | Multiple timestamps show subtle head/breath movement, gaze and blink; deterministic bounded motion without sudden jumps or stereotyped rapid oscillation; inspect full short sequence, not a single screenshot |
| H03 | Speech follows actual audio | Existing utterance-bound TTS/viseme events drive mouth; silence, stale callbacks, interruption and pause close it; cosmetic idle movement cannot fabricate speech |
| H04 | Work effects reflect real state | Agent/document/effect arrival consumes existing real task/tool/work-state signals; idle/approval/blocked states cannot display invented successful work, worker count or completion |
| H05 | Stop and recreation are safe | Cancel/pause/detach/replace stop transient work and speech effects; resume reflects current state without replaying old arrivals or callbacks |
| H06 | Reduced motion is respected | Cosmetic position/orbit/blink phases freeze or are disabled when motion is off; static state labels remain useful; no animation timer is required for correctness |
| H07 | Rendering is bounded | Finite positions/alpha, balanced painter state, bounded primitives/particles and no growth over repeated frames; respect existing viewport/clipping and runtime resource policy |
| V01 | Desired voice/style | Actual listening comparison with reference and real selected offline TTS/voice output; voice quality cannot be passed by UI/source tests |
| G01 | Spoken first-run guidance | Existing app starts guidance with explicit controls; spoken actionable steps match actual available controls and permission state; no simulated test completion |
| G02 | Guided tests are real | Each instruction/test has observable result, retry/skip/cancel behavior and honest unavailable/failed status; agent action remains behind existing owner/tool authorization |
| F01 | Fold7 acceptance | Actual authorized Fold7 video/audio/screenshots and lifecycle/resource measurements; emulator, Java2D and host logs cannot satisfy this row |

Host renders must identify exact source SHA, renderer/backend, window size,
density, fonts, motion setting, timestamp and synthetic/live input origin. A host
Java2D render of production painter code is useful comparison evidence but is not
an Android/Filament/device screenshot. Pixel comparisons use aligned same-size
regions; whole-frame similarity against the concept film is not a meaningful
layout metric under the owner's clarified instruction.

## Decision rules

Use PASS, FAIL, BLOCKED or NOT_RUN for every reviewed criterion, with evidence.
Do not promote a source-only/render-only increment to full reference acceptance.
Specifically, bounded 2D motion may pass H02/H06/H07 contracts while H01 remains
FAIL or NOT_RUN. A real 3D mesh alone is insufficient for H01 appearance acceptance.
Reference capability claims remain distinct from independently executed tasks.

Frozen source/code changes, test commands, failures, raw outputs and independent
results will be added in separate evidence files without rewriting these criteria
to fit the candidate. Main, signing, release, owner facts and media privacy stay
outside this lane's scope.
