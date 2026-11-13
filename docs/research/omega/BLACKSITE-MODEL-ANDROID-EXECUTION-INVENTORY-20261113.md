# BLACKSITE model and Android execution inventory — 2026-11-13

Audited source: `bfed3da87014b54fd81176d2e416bea19f01b483`. Thermal PR comparison: `c02292f43ebc072db304f3f8ba558c88ae72796f`.
This is a source inventory, not a device/session receipt. Installed models, live native state, active app agents, Termux availability and Fold7 LoRA performance are **UNKNOWN / NOT_MEASURED** here.

## Models and actual orchestration

| Component | Actual connection and limit |
| --- | --- |
| Gemma 4 E4B selection | [ContinuumSelectedModels](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/ContinuumSelectedModels.kt#L11) stores the selected ID/version/digest. Selection is metadata. Import checks bytes/SHA-256/native capabilities; load checks the stored source again. No imported/loaded/running claim follows from this constant. |
| Compatible LiteRT-LM models, including Qwen policy | [LocalModelHost](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/localmodel/LocalModelHost.kt#L24) owns one process catalog and guarded runtime. [Native load](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt#L133) uses CPU explicitly, despite GPU/NPU capability enums. Only one generation is admitted; fresh owner prompts rebuild isolated conversation state. Qwen-specific prompt policy does not prove a Qwen file is present. |
| DeepSeek V4.1 Flash-1017 | A selected research model ID/source/status string in `ContinuumSelectedModels`; that file explicitly does not establish a connected inference server, local weights or execution rights. |
| SWB1 / tiny neural dialogue | Existing local learning/retrieval components, not the Gemma engine. `ContinuumLearningCore` clones committed encoders, evaluates frozen holdouts and archives accepted snapshots. `NeuralPracticeScheduler.practiceOnce` is capped at 200 updates and shares its global RUNNING admission, owner/PAUSED/resource gates and compare-and-save. |
| CRE1 | [ContinuumCognitiveRuntime](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/learning/ContinuumCognitiveRuntime.kt#L64) restores a bounded checkpoint; default use is shadow scoring. Active reranking additionally requires `Cre1PromotionGate.readQualified` bound to its exact digest/steps. Only evidence already authorized in that dialogue turn is eligible. |
| SWB2 / CDX1 | Existing research/quality-audit paths in [ContinuumLearningCore](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt#L851); they do not replace the SWB1/CRE1 memory gate or native chat model. No experimental checkpoint is promoted by this audit. |

Production chat path:
`AgentActivity.runLocal` → inline `AgentModel` / `AiContextCompiler` → `generateLocal` → shared `localRuntime.stream` / `respondToTools` → `LocalModelThermalGuard` → `LiteRtLmLocalModelRuntime`.

The inline path is the live integration. [ContinuumLocalAgentModel](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/cognitive/ContinuumLocalAgentModel.kt#L23) explicitly identifies itself as a research adapter lacking the live path's screen/tool/history consent integration. `ContinuumCodingPlanning.model` and `LocalModelReasoningAdapter` use isolated `streamThinking` on the same host; they do not create a second engine.

[runLocal gates](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/AgentActivity.kt#L11405) retain registered-tool admission, owner approvals and a four-tool / one-retry default. Coding planning has zero tools/retries and one adapter invocation. `AgentLimits` additionally bounds state-machine steps (default 32). `generateLocal` has output/stall bounds and optional caller deadlines; ordinary productive streaming is not falsely described as having a fixed total wall-time limit.

## Three app team roles

[ChiefBotTeam](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/chief/ChiefBotTeam.kt#L54) configures one chief plus exactly two specialists, then uses `ChiefWorkerPool(1, 1)`: one executing worker, one queue position, two sequential stages.

| Slot | Existing execution and authority |
| --- | --- |
| Chief | Selects and coordinates the bound team/task. The text-team path does not perform a separate chief model call. Profile metadata grants no tools or verified capability. |
| Analyst | Registered capability `text.analyze`; one isolated local model call. Empty tool allowlist. No file, network, Accessibility, shell or signing authority. |
| Reviewer | Registered capability `text.review`; one subsequent local model call with the analyst response/hash. Same empty tool allowlist. Its critique is not an independent executed test. |

Actual chain:
`AgentActivity.showBotRoster` → `ChiefBotTeam.submit` → `ChiefDelegationCoordinator` → `ChiefJobs.agentJob` → `BoundedAgentOrchestrator` → `ChiefBotTextHandoff` → `generateLocal(streamThinking)`.

The team pins request ID, team revision, profile versions and model SHA. It requires explicit owner approval, rejects replay/stale admission, bounds total time to at most 120 seconds and records `RESPONSES_RECORDED`, not a verified device outcome. On restart active tasks become `INTERRUPTED / RESTART_REVIEW_REQUIRED`; they are not silently replayed. Twenty seeded profiles are registration records, not twenty running models. `BotRuntimeAvailability` derives availability from actual runtime state; no such device observation was obtained here.

Generic jobs can receive a job-scoped model/runner/policy/memory via `ChiefJobDeps`. `WorkerScope` checks tool scope at model and runner boundaries. `UnattendedApproval` never auto-confirms non-read-only actions. BUILD/VERIFY handoffs have distinct assigned workers, current grants, exact source/request hashes, durable authority claims, original remaining budgets and current evidence; profile names are not authority.

## Android-first coding execution

1. [Planning UI](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/AgentActivity.kt#L12097) → `ContinuumCodingPlanning`: owner pins source SHA and 1–3 allowed source files; model emits a requirements candidate only, with no repository access.
2. Reviewed canonical task/hash → [typed bridge](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/ContinuumTypedCodingBridge.kt#L106) `prepare` → `ContinuumMobileBuilderActivity`. Preparation and execution have separate explicit UI confirmations.
3. [dispatchApprovedCommand](https://github.com/Ullmann27/device-ai/blob/bfed3da87014b54fd81176d2e416bea19f01b483/app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt#L451) checks live activity, `com.termux.permission.RUN_COMMAND`, installed Termux and a free persisted result slot. Fixed Node executable/argument vector invokes `scripts/continuum-typed-coding-bridge.mjs` in the existing checkout; raw chat never becomes shell text.
4. Explicit one-shot Android callback → `ContinuumMobileBuildResultReceiver` → `ContinuumTypedCodingBridge.evaluate`: expected job/hash/source/exit status are checked. A reported patch stays pending owner review; the callback does not assert merge, install or device verification.
5. The older `ContinuumMobileCodingJob` is narrower: one validated JS-module/export test order and three fixed baseline/candidate/independent receipts. It is not arbitrary Android editing.
6. `ChiefBotHandoffAdmission.workspaceJob` → `ChiefWorkspaceWiring` exposes fixed `node-continuum`, `android-compile`, `android-unit` profiles through a supplied canonical port. This adapter does not prove that an executable worker or build toolchain is installed.

`ContinuumDeviceTeamleaderScheduler` restores an hourly persisted JobScheduler housekeeping job. `DeviceTeamleader.runOnce` serializes backlog recovery and DNA/learning status inspection; it does not launch shell, model coding, repository clone or APK build. Its READY state is explicitly limited to local bookkeeping.

## Scheduling, recovery and memory

- Existing daily `ContinuumAutonomousScheduler`: persisted network-free JobScheduler job, no replacement on every app open, one live service worker, owner PAUSED checks, a 15-second wall budget, Android resource sampling at 1.5-second intervals. Android may defer/stop it; scheduling is not proof of execution.
- Shared learning resource policy: known battery ≥20%, thermal below MODERATE when available, optional storage floor 128 MiB. The tiny-neural scheduler additionally latches a failed cancellation/owner/resource gate for the whole run; its new regression execution still belongs to source-bound QA.
- `ModelCheckpointStore`: checksummed/atomic bounded SWB1 history and digest-verified restore. `FileAgentCheckpointStore`: bounded checksummed agent journal. `BoundedAgentOrchestrator.resumeSaved` refuses automatic resume at/after tool proposal boundaries, preventing duplicate effects.
- Generic memory path: `LayersMemoryPort` → `ChiefMemory.recall` → bounded records, with unconfirmed material marked UNVERIFIED. The concrete two-stage text team deliberately uses `memory = null`; its handoff contains task/analysis and at most one approved skill, not private owner conversation.
- Production owner chat compiles the authorized current context; coding planning/bot tasks omit the owner's general memory store. `evaluationConfirmsOutcome = false` prevents an answer-quality check from certifying external action success.
- **Gemma/DeepSeek LoRA training on Fold7: NOT_MEASURED.** The inspected recurring learning code updates its own small research/encoder namespaces; this is not evidence of LoRA execution, native-LLM weight training or higher native-model quality.

## Thermal phase instrumentation from PR #787

PR head `61f1a06ee92f456489995f0ed7e3e49afaae30df` and master `c02292f43ebc072db304f3f8ba558c88ae72796f` contain identical blobs:

| File | Blob SHA |
| --- | --- |
| `systemintelligence/ThermalPhaseRecorder.kt` | `47cf60ae1a3e9de8b644cf2118da4a410fc23dbb` |
| `systemintelligence/ThermalPhaseRecorderTest.kt` | `94845dd7df83608c258ec3d34f5c085f2c0468a6` |
| `docs/qa/FOLD7-THERMAL-PHASE-MATRIX-20261112.md` | `71c3590b4135e6ad5767be5d5a711725dea3b79b` |

No port or second collector is needed. The existing recorder has eight phases and 2,000-sample capacity; it accepts numeric observations and explicitly reports `gpuMeasured=false`. Its four tests cover admission, invalid values, summaries and capacity. This inspection did not execute them.

The audited `AgentActivity` and `AndroidSystemDiagnosis` contain no `ThermalPhaseRecorder` reference. `AndroidSystemDiagnosis.capture` already reads thermal status and battery temperature, but also performs a 250 ms CPU measurement; it must not be reused as a per-frame sampler. The smallest remaining connection is a bounded owner-started phase session in the existing diagnostic flow, fed by existing numeric runtime/Android observations. Keep unavailable headroom/GPU values unknown. Reuse `SystemPerformanceEvaluator` for comparable median/p95 evaluation, not another statistics subsystem. Device phase traces are **NOT_MEASURED**.

## Demonstrated highest-impact admission gap

At the audited source, `LocalModelThermalGuard.guarded` sampled before registering its run. A SEVERE listener callback during the injected sampler, followed by the sampler returning its previously captured 0, was lost. `LocalModelThermalGuardTest.NativeStandIn` provides a deterministic reproduction seam.

Separately authorized correction: [`4f646cedbd839894b9d517e986f1e5e5ecd4d5bf`](https://github.com/Ullmann27/device-ai/commit/4f646cedbd839894b9d517e986f1e5e5ecd4d5bf), based on `205ef0f587d38c06ea4094820e8f92588cfd9bd5`. It latches heat across sampling/admission and protects cancellation ownership; all three generation entry points are covered by added negative cases. Compilation and test execution are **NOT_RUN** here; integration must obtain real CI results.
