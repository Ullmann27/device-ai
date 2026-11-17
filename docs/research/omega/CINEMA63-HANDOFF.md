# Cinema63 · Command03

Status: WIP / UNVERIFIED. Owner-requested visual and repair pass, 2026-11-17.

Base: 0b8600d1f38017e05266eb8d2365b6047424746d / PR #851. Integration target: integrate/continuum-command03-android-20261115. Responsible: current Continuum coding session.

Scope: existing Android HTML stage, cinematic lighting/depth, state-linked presentation, accessible controls, lifecycle correctness. Preserve original avatar/orb, native routes, permissions, package and signing identity. EXP-15 UI, EXP-12 lifecycle. No duplicate renderer.

Recent candidates checked: #860 RecallHits, #861 memory planner, #862 native Blender backgrounds, #864 native vacant hologram, #863 broader integration. #862/#864 do not change the default HTML stage. Their claims do not establish default-stage graphics coverage.

Acceptance: production DOM and browser viewport/state checks, original asset parity, Android compile/tests and real ARM64 APK. Device validation remains NOT_RUN until physical evidence. No main merge. No signing secrets accessed. Rollback: revert Cinema63 patch only.

Environment: sparse Code61 checkout exists; Code62 delta read through GitHub connector. Local Android SDK/Gradle cache absent, direct GitHub transport currently unavailable. Existing GitHub build path is the current Code62 release path. Full app-wide repair is not yet established.
