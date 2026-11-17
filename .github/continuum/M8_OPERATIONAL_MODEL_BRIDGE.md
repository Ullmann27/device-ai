# M8 – Operational local model loop: evidence gate and safe fallback

Research branch based on PR #284, `caaf69326aa0cecbb40a051064dd492ec4e40f1a`.
This is an **implementation milestone**, not a claim of a passed APK or a
verified increase in the model's reasoning capabilities.

## Existing production path: preserve it

`AgentActivity.runLocal()` already binds `AiContextCompiler`, the existing
streaming `LocalModelRuntime`, `ChatReasoningRouter`,
`BoundedAgentOrchestrator`, `ToolGate`, independent owner approval,
and the chat lifecycle. In particular it supports explicit internet consent
and native `respondToTools` continuation. Do **not** replace this with an
experimental adapter until a feature-parity and privacy review passes.

## Implemented fixes on this research branch

1. **No-tool evidence gate.** `BoundedAgent.hasFreshToolOutcomes()`
   now requires at least one fresh SHA-256 output receipt, at least one
   observation and all observations to have succeeded with nonblank outputs.
   An evaluator-only text answer remains `PARTIAL`; it cannot become
   `SUCCESS` or improve TOOL_USE reputation by vacuous truth.
   Existing unit-test expectations and paired mock-benchmark labels adjusted.
2. **True app entry fallback.** The previous `AgentActivity.runLocal`
   fallback rendered `LocalConversationContext.renderMemories(memories)`
   whenever the compiler failed or state was absent. This was capable of
   bypassing `SENSITIVE`, `STALE` and `CONTRADICTED` filtering.
   It now uses `AgentContextFallback.render(reflection)`: no MemoryRecord
   parameter exists, so unfiltered records cannot be passed through it.
   The original user task is already included before the fallback.
3. **Optional standalone AgentModel bridge.** `ContinuumLocalAgentModel`
   accepts a trusted identity/context template, compiles current task and
   selected memories through `AiContextCompiler`, starts a bounded
   `LocalModelRuntime.stream` pass, maps text and a single structured
   tool proposal into `AgentModelOutput`, enforces deadlines and
   bounded output, checks model identity, rejects tool-call errors,
   and exposes cancellation through `CancellableAgentModel` without
   changing the existing AgentModel SAM signature.
   Tool proposals are *not* executed by this bridge. ToolGate and owner
   approval stay in the existing bounded orchestrator.
4. **New unit tests (written, not executed on the actual app project).**
   `ContinuumLocalAgentModelTest`, `AgentContextFallbackTest`,
   and updates to `BoundedAgentTest`, `AgentExperienceRecallTest`,
   `AgentExperiencePairedBenchmarkTest`.

## Safety and integration conditions

- `ContinuumLocalAgentModel` is a **research adapter**, NOT yet installed
  into live `AgentActivity.runLocal`; the live app continues to use its
  currently richer model callback with `respondToTools` and web consent.
- Blocking generation must be called from a worker thread only.
- The returned text and proposals remain untrusted; they cannot approve tools.
- A model failure/timeout raises an exception, cancels the runtime and must
  be handled by the caller. There is **no** silent remote/cloud fallback.
- Reject the entire agent context on compilation truncation; no partial
  owner-fact or governance context is promoted as complete.
- Existing `evaluationConfirmsOutcome = false` in live chat remains unchanged.
- Do not promote recorded outputs into verified skills without independent
  receipts and owner review; the separate `VerifiedSkillReview` defaults DENY.
- Preserve CRE1, EWM/SWB, neuro-inspired experiments, owner permissions,
  signed APK update lane and existing installed app data.

## Proof still required before merge and next APK

1. Run `./gradlew testDebugUnitTest` (and actual applicable variant
   tasks), focused BoundedAgent, Context, AgentActivity and adapter tests.
2. Run Node contract tests; validate combined state against all relevant PRs.
3. Test normal direct local model, regular tool proposal, denied tool,
   cancellation, slow model, incorrect model identity, hostile memory
   and poisoned tool outputs on the Fold7.
4. A/B compare the **existing live** model callback with this research
   bridge on the same local model and task set, keeping owner/web behavior
   equivalent; do not swap the production binding by assumption.
5. Independently verify coding-agent repair outcomes (FAIL->PASS + regressions).
6. Only after a green combined build, create and validate an APK signed
   with the existing owner's certificate and monotonic versionCode.

### Environmental blocker

This session has GitHub source read/write access but no repository mount,
Android SDK/Gradle project checkout or attached Fold7 device. There are
no workflow/status checks on these research commits. Kotlin unit tests
have been committed, not executed in the actual project. No APK has
been built and no measured on-device intelligence gain is claimed.
