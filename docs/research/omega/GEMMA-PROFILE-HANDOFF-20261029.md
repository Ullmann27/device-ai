# Monotonic Gemma runtime profiler — WIP / UNVERIFIED

Baseline: PR #218, 0b2a9dddfe172eac82923092fcdaa974afc6e25a.
Branch: research/gemma-monotonic-profile-20261029.
Scope: EXP-05 local runtime diagnostics; EXP-11 existing chat integration; EXP-12 turn boundaries.

## Approved narrow design
Add a bounded (8 recent turns) process-memory recorder containing enums, counters, monotonic nanosecond offsets and numeric JVM heap used/max samples only. The default LocalModelRuntime.inferenceProfiles getter preserves existing fake implementations. LocalInferenceMetrics and LocalInferenceEvent constructors remain unchanged. safeSummary renders fixed labels/enums/numbers, at most2500characters. No prompt/answer text, model names, model paths, error messages, credentials or owner facts enter the profiler. There is no filelogger/network/service.

Measure invocation, previous-conversation release/create, dispatch enter/return/throw, first nonempty native text callback, native terminal callback entry/exit, cancel request/call exit and runtime terminal delivery entry/exit. Native prefill remains UNKNOWN_NOT_EXPOSED; first content callback is not an actual first-token or prefill measurement. Heap samples explicitly mean JVM_HEAP_ONLY, never total Android/native/GPU/PSS memory. Parent agent owns Activity lifecycle/status-copy/timeout wiring.

## Safety
Keep the original runtime and its existing lifecycle/callback locks unchanged. Profiler clock/heap suppliers and measured native/user actions execute outside profiler monitors. This is instrumentation only; it must not import PR221's quarantined callback-gate rewrite or claim to fix cancellation/120s behavior. Existing ToolGate, catalog integrity, one native runtime and fresh full-prompt isolation remain.

## Checkpoint / verification
Initial checkpoint authors deterministic JUnit cases before implementation. Kotlin/JUnit, Android compile/lint, Node, APK and device execution: NOT_RUN; execution environment UNAVAILABLE. No red-green run is claimed. Tests should later run through the existing approved project QA command, including LocalInferenceProfileTest and existing localmodel/cognitive suites. No GitHub Actions, downloads, prompt/backend/budget/model changes, main merge or installation.

## Next / rollback
Implement LocalInferenceProfile.kt and narrowly wire actual runtime boundaries. Review exact API0.18.0 signatures and source diff/read-back; parent integrates selected files. Revert this isolated branch's selected profiler files to remove instrumentation. The ordinary chat120s deadline currently begins only after start(callback) returns; measure that synchronous invocation gap, do not guess the phone's cause. Do not read or modify Opus TEST4.
