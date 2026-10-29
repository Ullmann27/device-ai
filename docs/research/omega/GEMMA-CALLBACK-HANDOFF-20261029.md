# Gemma callback lifecycle audit — WIP / UNVERIFIED

Date: 2026-10-29. Official repository: Ullmann27/device-ai.
Baseline: PR #216, b47f4fac043b2124a9ef4dd1cfb469a3fde88bc6.
Branch: research/gemma-stream-callback-lifecycle-20261029.
Scope: EXP-05 local inference; EXP-11 existing main-chat lane; EXP-12 conversation isolation.

## Source findings
- LiteRtLmLocalModelRuntime discards whitespace-only Content.Text chunks with isBlank(). The LiteRT-LM 0.18.0 MessageCallback contract explicitly describes incremental chunks, so spaces/newlines are meaningful output.
- Internal output-limit and multi-tool rejection call cancelProcess and immediately finishInference. This exposes READY before the native asynchronous terminal callback; terminalOverrideRequested then ignores that callback.
- Owner cancel does not suppress queued chunks or choose Cancelled over a racing onDone.
- Upstream v0.18.0 runtime/conversation/conversation.h documents CancelProcess as poisoning its conversation; subsequent SendMessageAsync on that object is unsupported. Fresh full-prompt calls already replace the native conversation by design. Tool continuations must fail closed after cancel.
- Main-chat history is manually bounded through LocalConversationContext; no change to persistent memory or owner facts is proposed.

## Plan/checkpoint
Test-first checkpoint: a6f8e436143a98bea7ae8be9820434c3a77f82c6 (Draft PR #221). The follow-up implements LocalInferenceCallbackGate within LiteRtLmLocalModelRuntime.kt and wires the actual MessageCallback/cancel/unload path through it. It preserves nonempty whitespace chunks, suppresses late output, preserves the first terminal cause and holds lifecycle ownership until native terminal callbacks. Tool replies require a successful preceding native terminal; cancelled/failed conversations are replaced only by the existing fresh full-prompt path. The existing native Engine, model identity/hash checks, ToolRegistry, automaticToolCalling=false and explicit ToolGate remain.

The six JUnit cases cover whitespace/newlines, cancellation versus native completion, reentrant cancellation preserving the original failure, cancellation before native dispatch, late callbacks after success and repeated cancellation. These exercise the same production arbitration helper with controlled native callback boundaries; they do not execute JNI or prove physical-device behavior.

## Verification
Status: WIP / UNVERIFIED. Kotlin/JUnit, Android compilation, lint, APK and physical-device execution: NOT_RUN / execution environment UNAVAILABLE. No red/green run was possible; no test pass is claimed.
Planned command:
`./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.localmodel.LocalInferenceCallbackGateTest --tests at.heinz.deviceai.localmodel.LocalConversationBoundaryTest --tests at.heinz.deviceai.cognitive.LocalModelReasoningAdapterTest`
Then existing debug compilation/lint/suite under the project-approved QA variant. No GitHub Actions or model downloads.

## Handoff / rollback
Continue by reading the exact branch HEAD and reviewing the runtime diff, then run real tests when an environment exists. Native API signatures and callback/poisoning documentation were inspected at upstream v0.18.0; no third-party source code was copied. AgentActivity is owned by the parent agent; visible streaming and main-chat timeout changes belong there. Device latency root cause remains UNCONFIRMED: source findings do not prove why the real phone hit 120 seconds. Roll back by dropping this isolated branch or reverting its selected commits. Never merge main or install an APK as part of this checkpoint.

## Parent integration notes
- AgentActivity.generateLocal must wire TextDelta to an explicitly unverified preview while preserving final quality checks and tool authorization. Current baseline only accumulates response text until terminal.
- Its 120s deadline starts after start(callback) returns, although start can synchronously create/close a conversation and native SendMessageAsync does synchronous prompt conversion. Source diagnostics alone do not establish which stage consumed the phone's time. Measure invocation/prefill/first-text/terminal separately, without logging private prompt text.
- Existing LocalConversationContext contributes at most four truncated turns as unverified data; fresh native conversation is deliberate isolation, not implicit persistent chat history.
- No prompt budget, thinking template, token limit, backend or model changes belong to this patch.

## DO NOT INTEGRATE — native lock-order risk found in review
The full callback-gate rewrite at ec419d51eeaa061c7863ee5c4f943b236c10cc94 remains an isolated research candidate, not the recommended patch. Native v0.18.0 ThreadedExecutionManager::CancelAllTasksInSession takes session_and_task_lookup_mutex_, while some error/cancellation paths invoke user callbacks under the same mutex (FinishTask's invoke_callback_and_return; UpdateAllTasksToState). Holding the Kotlin callback monitor while calling cancelProcess or sendMessageAsync can invert locks with a native cross-thread callback. The same-thread reentrant JUnit case does NOT exclude that deadlock. Tests were NOT_RUN. The root integration selects only the original runtime's isBlank()->isEmpty whitespace correction; it must not select this whole rewrite. The lifecycle/cancel race and 120s mechanism remain unresolved.

Next work requires moving JNI and externally supplied callback invocation outside state monitors while preserving exactly-once terminal delivery, ordered deltas, per-inference ownership and cancellation-before-dispatch. Add deterministic tests where native send/cancel waits for a callback from a separate thread; retain the production native API/ToolGate boundaries. Do not turn source inspection into device proof.
