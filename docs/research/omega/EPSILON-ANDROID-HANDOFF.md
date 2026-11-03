# EPSILON B — native optional 3D runtime / independent rig challenge

Status: WIP / UNVERIFIED. Base 7e83281a4fc5f7bd925adb56951237a31e560cf6, unchanged Android source from Chief verified 64ec06a7.
Branch codex/epsilon-android-20261103. EXP-05/09 compatibility only; no brain/runtime/ToolGate replacement or promotion.

Owned paths: `avatar3d/*.kt`, related unit tests, `ContinuumHologramView.kt`, `app/build.gradle.kts`, `tools/epsilon-avatar-contract`, evidence and this handoff. AgentActivity is read only. Geometry team independently owns GLB, Blender source and assets.

Existing call graph: AgentActivity alone drives AvatarDirector from current runtime flags and its one TTS progress listener. Director attaches ContinuumHologramView, which already owns AvatarPoseReducer and one lifecycle-aware Canvas loop. New renderer consumes that pose; no second reducer, listener, brain, camera, network or speech text retention. Original orb remains default, native 2D fallback remains available.

First increment: independent GLB validation criteria frozen before inspecting geometry team outputs, actual binary metadata validation and bounded pure pose mapping. Subsequent optional Filament renderer only against verified official pinned APIs. Full Gradle jobs owned by QA; pure focused Kotlin checks locally. No APK sign/install/release.

Read: AGENTS, current state, Frontier handoff, experiment contract/register, issue41, PR287, avatar contract, avatar-live results, hologram design, actual AvatarDirector/Contract/renderer/view/Activity calls. Device/GPU/visual performance and face likeness NOT_RUN.

Rollback: revert this branch; default orb and existing 2D remain. Next: freeze independent contract, pin/read official Filament artifacts, implement first increment and focused tests.

First source increment: 6 focused JVM JUnit tests PASS for metadata negatives, named mappings, gaze, reduced motion, stale/stop utterance handling through existing reducer. Optional native path WIP/UNVERIFIED: pinned Filament + gltfio 1.56.0 official Maven Central artifacts/source inspected, Apache-2.0, arm64-v8a/armeabi-v7a/x86/x86_64. No ModelViewer or utilities dependency. Metadata validator runs before JNI; native engine exists only for visible selected validated asset/surface, detached/paused/thermal/power save releases native resources. Asset is not bundled on this Android branch; absent asset gives existing 2D fallback. Geometry branch supplies independently reviewed candidate.

Current code needs focused Android API compile, stronger binary index/skin validation, independent geometric validator implementation and QA review. Full Gradle/device/GPU NOT_RUN. Draft PR https://github.com/Ullmann27/device-ai/pull/405.
