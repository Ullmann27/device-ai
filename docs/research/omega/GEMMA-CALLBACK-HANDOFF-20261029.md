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
Tests are authored before implementation against a proposed internal callback arbitration helper in the existing runtime file. This helper is not another runtime or inference path. It will preserve nonempty whitespace chunks, suppress late output, preserve first terminal cause and hold lifecycle ownership until native terminal callbacks. The existing native Engine, model identity/hash checks, ToolRegistry, automaticToolCalling=false and explicit ToolGate remain.

## Verification
Status: WIP / UNVERIFIED. Kotlin/JUnit, Android compilation, lint, APK and physical-device execution: NOT_RUN / execution environment UNAVAILABLE. No red/green run was possible; no test pass is claimed.
Planned command:
`./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.localmodel.LocalInferenceCallbackGateTest --tests at.heinz.deviceai.localmodel.LocalConversationBoundaryTest --tests at.heinz.deviceai.cognitive.LocalModelReasoningAdapterTest`
Then existing debug compilation/lint/suite under the project-approved QA variant. No GitHub Actions or model downloads.

## Handoff / rollback
Continue by reading the exact branch HEAD and the runtime file, implement only this callback fix, review API0.18.0 signatures, then run real tests when an environment exists. AgentActivity is owned by the parent agent; visible streaming and main-chat timeout changes belong there. Device latency root cause remains UNCONFIRMED: source findings do not prove why the real phone hit 120 seconds. Roll back by dropping this isolated branch or reverting its selected commits. Never merge main or install an APK as part of this checkpoint.
