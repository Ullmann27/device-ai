# Reference-driven native avatar motion

Status: WIP / UNVERIFIED. Baseline `d8a927dd6ced4dea489e2042afb4b5624157fa3b`.
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
Foreign Chief `7247e99d` separately contains `avatar3d`/Filament and its optional
view path; that work is not blindly imported or described as this 2D renderer.

Proposed production scope: only `avatar/ContinuumAvatarRenderer.kt`, with bounded
head motion, holographic contours/orbits and work effects driven by existing
AvatarState. No new scheduler, state machine, voice engine, permission, assets or
model. No changes to Activity, ToolGate, Manifest, theme or dashboard geometry.
EXP-05/EXP-11 remain upstream signal sources; their authority is unchanged.

Verification plan: preserve exact production-renderer baseline frames, add
meaningful motion/reduced-motion/work-state contracts, render before/after on the
same existing Java2D painter, then independent review. Host preview is not an
Android/Fold7 screenshot, GPU benchmark or visual parity claim. Audio identity,
real TTS synchrony, device thermal/FPS and final visual approval remain unrun.

Rollback: revert this isolated renderer increment; no persistence migration or
owner data is involved. The existing orb and 2D selection remain available.
