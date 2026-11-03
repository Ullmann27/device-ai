# EPSILON B — native optional 3D runtime / independent rig challenge

Status: WIP / UNVERIFIED. Base 7e83281a4fc5f7bd925adb56951237a31e560cf6, unchanged Android source from Chief verified 64ec06a7.
Branch codex/epsilon-android-20261103. EXP-05/09 compatibility only; no brain/runtime/ToolGate replacement or promotion.

Owned paths: `avatar3d/*.kt`, related unit tests, `ContinuumHologramView.kt`, `app/build.gradle.kts`, `tools/epsilon-avatar-contract`, evidence and this handoff. AgentActivity is read only. Geometry team independently owns GLB, Blender source and assets.

Existing call graph: AgentActivity alone drives AvatarDirector from current runtime flags and its one TTS progress listener. Director attaches ContinuumHologramView, which already owns AvatarPoseReducer and one lifecycle-aware Canvas loop. New renderer consumes that pose; no second reducer, listener, brain, camera, network or speech text retention. Original orb remains default, native 2D fallback remains available.

First increment: independent GLB validation criteria frozen before inspecting geometry team outputs, actual binary metadata validation and bounded pure pose mapping. Subsequent optional Filament renderer only against verified official pinned APIs. Full Gradle jobs owned by QA; pure focused Kotlin checks locally. No APK sign/install/release.

Read: AGENTS, current state, Frontier handoff, experiment contract/register, issue41, PR287, avatar contract, avatar-live results, hologram design, actual AvatarDirector/Contract/renderer/view/Activity calls. Device/GPU/visual performance and face likeness NOT_RUN.

Rollback: revert this branch; default orb and existing 2D remain. Next: freeze independent contract, pin/read official Filament artifacts, implement first increment and focused tests.
