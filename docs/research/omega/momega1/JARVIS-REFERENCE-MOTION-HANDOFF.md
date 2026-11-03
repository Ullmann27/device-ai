# Reference-driven native avatar motion

Status: WIP / PARTIAL CONTRACT EVIDENCE; appearance is NOT_ACCEPTED and device
execution is FOLD7_NOT_RUN. Baseline `d8a927dd6ced4dea489e2042afb4b5624157fa3b`.
Branch `codex/jarvis-reference-ui-20261103`, target `codex/momega1-chief-20261103`.

The owner's latest clarification is authoritative: preserve the existing agreed
Continuum design, including its colours, typography, buttons and layout. The
orange-marked video is a reference for effects, head movement, speech behaviour
and working animation only. It does not replace the existing design.

Primary video: `gemini_generated_video_33fd6ada.mp4`, 10.026667 seconds, 1280×720,
24 fps; SHA-256 `a9c525346b200c2aa062ff9d2c7495f8492fbaa9016a4cf125364dda77b35893`.
Private media stays outside Git. The other four videos are functional inspiration,
not proof that their advertised external systems work inside Continuum.

Existing code inventory: `ContinuumAvatarRenderer` is an optional procedural 2D
head using the shared native/Java2D painter, existing Director/reducer and the
existing HologramView motion/lifecycle policy. The original orb is retained.
Foreign Chief `7247e99d0b93b62dfb2b5eee22486d22911cb722` contains a separate,
optional real 3D path. PR404 supplies the CC0 humanoid geometry; PR405's final
source `7caef9b624beebdb78c8c081a17f4978461ae496` supplies its bounded native
Filament integration. The existing 2D renderer is unchanged and remains a fallback.

After the owner's humanoid-head clarification and the Chief's path approval, the
scope is a narrow, exact import from PR405: `avatar3d`, bundled `assets/avatar3d`,
the optional path in `ContinuumHologramView`, the two Filament 1.56.0 dependencies,
focused native contract/lifecycle tests and their existing build helpers. PR408's
layout changes are excluded. The native renderer shares the existing Director,
reducer, frame clock and lifecycle policy; no new scheduler, TTS or permissions.
`AgentActivity`, ToolGate, Manifest, theme, typography and dashboard geometry are
unchanged. Existing owner renderer preferences are not changed.

The bundled real GLB is 8,690,400 bytes, SHA-256
`f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e`.
Its CC0 MakeHuman provenance, morph targets and joint names were inspected.
Thirty upstream host-render anchors demonstrate actual humanoid geometry and
depth, but the independent reviewer rejected reference appearance: the skin is
too dim/opaque, the wires sparse and the eyes/lips insufficiently readable.
Those host renders are not Filament or Fold7 screenshots.

Independent RED on the unchanged PR405 source: reviewer freeze `8c43f017`,
12 actual Kotlin/JUnit cases, 10 PASS and 2 FAIL: idle head and idle gaze never
move. The other ten cover bounds, event gaze, speech provenance, blink, reduced
motion and buffer reuse. These are presentation contracts, not visual acceptance.
Source import is WIP; the original eight tests and API compile have not yet been
rerun in this environment. The shared benchmark has CPU priority.

Next: add bounded natural idle movement using the existing render clock, then
run the same frozen tests and all affected native contracts. Observed face-position
gaze, if integrated, must expire, yield to real task state and remain distinct
from cosmetic idle gaze. Material/look changes require same-conditions real GLB
renders and independent review. Audio identity, real TTS synchrony, device
thermal/FPS, actual work-event effects and final visual approval remain unrun.

Rollback: revert this isolated import/increment or leave the optional 3D choice
disabled; no persistence migration or owner data is involved. The existing orb
and 2D selection remain available.
