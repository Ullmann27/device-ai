# Cinema fluid-light integration — 2026-11-10

Base: PR #706, `17491f4d8103f0e55836369333fab07e80e6e9c2` (live verified).
Canonical development target: `cinema/fluid-light-stage-20261110`; integrator: current Codex session.

WIP / UNVERIFIED. First checkpoint before implementation.

- Extend JarvisCinemaEntrance / ShellRenderer and existing GlassBackend with bounded fluid light; preserve real event clock.
- Avatar work owns avatar/ and avatar3d/ only: existing projected window light, temporal cleanup, no replacement asset.
- Shader work owns JarvisGlassRenderer only. No duplicate compositor or event bus.
- Cognitive research stays on research/continuum-context-cinema-20261110; shadow-only validation and seven-day plan.
- QA uses local Kotlin/JUnit, Java2D and available Android toolchain. Device/GPU/Fold7 NOT_RUN until measured.
- Original release, package, main, model weights, signing and owner data unchanged. No paid services.

Compatibility: existing EXP-05/06/07/12 and Cinema EXP-15 thermal controls. Rollback: revert this development branch's commits only.

Acceptance: real command/work/result/cancel/error inputs, deterministic sequence and lifecycle tests, before/after host renders explicitly labelled Java2D, exact-source build results and separately reported device acceptance.
