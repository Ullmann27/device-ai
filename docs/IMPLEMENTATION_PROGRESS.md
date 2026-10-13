# Implementation Progress

**Current phase:** Part 5 (local inference), continuing on the Part 4 stack above Part 3.
Parts 1–2 are merged to `main`. This PR is not approved for merging.

## Status
| Module | Status |
|---|---|
| Cognitive identity, behavior, self-model and adaptive identity | IMPLEMENTED |
| Typed memory, provenance, claims, retrieval, write policy and graph | IMPLEMENTED — bounded agent now uses explainable hybrid lexical/entity/temporal/validation ranking with optional semantic/source-trust hooks |
| Experience records/store and similarity retrieval | IMPLEMENTED |
| Model adapters, evaluated reputation and explainable routing | IMPLEMENTED |
| Source identity, evaluated source trust and domain profiles | IMPLEMENTED |
| Versioned archive, typed encoding, identity binding and LKG recovery | IMPLEMENTED |
| JVM `IdentitySigner` contract | INTERFACE ONLY |
| Android Keystore EC signer | IMPLEMENTED |
| Typed/versioned skills, dependency validation, activation and audit | IMPLEMENTED |
| Registered-tool proposals, bounded argument validation and risk gate | IMPLEMENTED |
| Tool execution receipts and replay suppression | IMPLEMENTED — bounded versioned file store, integrity verification, atomic replacement and STARTED ambiguity blocking; not universal exactly-once |
| Explicit confirmation contract and stronger destructive approval path | IMPLEMENTED |
| Confirmation UI and read-only operator-policy wiring | IMPLEMENTED — read-only allowed by explicit app policy; confirm/destructive calls require UI approval |
| Cancellation of an already-running Shizuku shell process | PARTIAL |
| Bounded orchestration, memory retrieval, evaluated experience and model reputation update | IMPLEMENTED |
| Allowlisted checkpoint codec and safe pre-tool resume | IMPLEMENTED |
| Automatic process-death checkpoint persistence / exactly-once tool recovery | PARTIAL — durable pre-tool checkpoints and durable replay suppression; Android shell side effects cannot provide universal exactly-once recovery |
| Read-only HTTPS research provider, source metadata and citations | IMPLEMENTED |
| Baseline claim extraction and independent-domain corroboration | IMPLEMENTED |
| Claim-language contradiction/support quality beyond deterministic heuristics | PARTIAL |
| Evaluated source trust update and MemoryWritePolicy research path | IMPLEMENTED |
| Detailed source-trust/model-reputation evidence persistence | PLANNED |
| Approval UI, MainActivity integration and production orchestration wiring | IMPLEMENTED — normal LOCAL/AUTO uses LiteRT-LM, BoundedAgentOrchestrator and ToolGate; manual expert shell remains isolated in MainActivity |
| SHA-256-verified `.litertlm` import/catalog and persisted capability manifest | IMPLEMENTED |
| CPU LiteRT-LM load/unload, streaming, cancellation and usage metrics | IMPLEMENTED |
| Registry-only structured tool proposals with automatic tool execution disabled | IMPLEMENTED |
| Device/emulator inference using imported FunctionGemma model bytes | PARTIAL |
| LiteRT-LM integration with AgentActivity, SAF import UI and the Part 4 orchestrator | IMPLEMENTED — model import/load states and registry-only proposals are wired; real device inference is not verified |
| llama.cpp fallback/benchmark runtime | PLANNED |

## Part 3 validation
- Standalone Kotlin 2.4.20 compilation of all cognitive production sources passed.
- All 44 cognitive JVM tests passed in the prior Part 3 work.
- Latest inherited Part 3 head was validated by GitHub Actions run `38294495441` on this
  branch: `:app:testDebugUnitTest` and `:app:assembleDebug` both succeeded.
- The earlier workflow runs `38294373459` / `38294249300` on the base branch ended
  `action_required` with zero jobs; run `38294495441` is the successful full validation.

## Part 4 changes
- Added `AgentToolLayer.kt`, `BoundedAgent.kt`, `Research.kt`, and
  `AndroidKeystoreIdentitySigner.kt`.
- Added deterministic skill/tool, agent/checkpoint and research tests.
- Updated `CognitiveCore` to accept typed skills and tightened the existing risk levels for
  app launches and screenshots so they require confirmation.
- Added `docs/AGENT_TOOLS_AND_RESEARCH.md`.
- Upstream reference paths and license details are recorded there; implementation adapted
  patterns without direct source-code copying.
- The Part 4 workflow run `38297272299` ended `action_required` with zero jobs and no failed
  job logs. This is an external workflow-approval gate, not an observed compile/test failure.

## Part 5 changes
- Added `app/src/main/java/at/heinz/deviceai/localmodel/LocalModelCatalog.kt` and
  `LiteRtLmLocalModelRuntime.kt`.
- Added the pinned-version dependency `com.google.ai.edge.litertlm:litertlm-android:0.18.0`.
- Added `LocalModelCatalogTest.kt` for hash, size, metadata, version and capability validation.
- Function-call schemas are derived only from registered `ToolSpec` entries; calls are emitted
  as typed proposals and never automatically dispatched by LiteRT-LM.

## Earlier Part 5 validation and limitations
- Kotlin 2.4.20 standalone compilation of cognitive, agent, research and local-model production
  sources passed against Android 36 and LiteRT-LM API-shape stubs. This checks Kotlin source
  consistency but does not verify the downloaded `0.18.0` AAR's binary compatibility.
- All 66 available root, cognitive and local-model-catalog JVM tests passed using JUnit 4.13.2.
  Standalone production compilation uses stubs for the Android-bound executor and LiteRT-LM
  library, so it does not replace the Android Gradle build.
- Local `gradle :app:testDebugUnitTest :app:assembleDebug` could not resolve Android Gradle
  Plugin 8.10.0 in this sandbox; no `gradlew` is checked in. The current task's Android validation
  must be taken from the new PR branch workflow run.
- Keystore instrumentation cannot be executed in this sandbox. The JVM signer contract tests
  are not a claim of a device-backed Keystore run.
- Checkpoints are not automatically persisted and resume is prohibited after tool execution.
  The legacy Shizuku operation cannot be cancelled remotely; the agent gate can cancel its
  waiting worker but not terminate an already-running shell process.
- Detailed source/model trust evidence is still in-memory; only the existing Part 3 summary
  identity state is archived. A bounded, versioned evidence archive remains deferred.
- Research claim comparison is a deterministic baseline, not a semantic evaluator or a
  substitute for independent expert verification. No live-network endpoint was exercised by
  the deterministic tests.
- LiteRT-LM `0.18.0` passed the dependency advisory check. This environment could not resolve
  Google Maven to download the AAR; native Android build validation must therefore come from
  GitHub Actions after the workflow approval gate is cleared.
- The catalog supports local SAF byte import only; no model weights are bundled or downloaded.
  FunctionGemma mobile-optimized 270M is the documented small-model path and remains subject
  to its separate Gemma terms. No real model bytes or on-device inference run are claimed.

## Deep Research P0 continuation
- Added `FileAgentCheckpointStore` with bounded binary envelopes, SHA-256 integrity validation,
  atomic replacement, corruption rejection and explicit discard.
- `BoundedAgentOrchestrator` now persists state transitions and can conservatively resume only
  pre-tool states; checkpoints at/after the tool proposal boundary require operator review so a
  process restart cannot silently duplicate a side effect.
- Added duplicate `callId` rejection inside `ToolGate`.
- Replaced the bounded agent's token-overlap-only memory lookup with an explainable hybrid
  retriever combining BM25-like lexical relevance, tag/entity overlap, temporal relevance,
  validation/provenance quality, recency and optional semantic/source-trust scorers.
- Added deterministic tests for checkpoint corruption/recovery, replay rejection and hybrid
  ranking/privacy behavior.

## Durable receipts and local-first chat
- `FileToolExecutionReceiptStore` persists bounded, schema-versioned receipts through an atomic
  file replacement and SHA-256 integrity envelope. Persisted fields contain only tool identity,
  sanitized arguments, a fingerprint, risk, transitions and bounded output length/hash metadata.
- `ToolGate` records proposals before approval, persists APPROVED and STARTED before execution,
  and blocks retries for prior STARTED and terminal records. A previously SUCCEEDED call is
  reported as already completed without running it again. The guarantee is durable replay
  suppression and ambiguous-outcome blocking, not exactly-once Android shell execution.
- AgentActivity AUTO/LOCAL routes to the imported LiteRT-LM runtime. Schemas come from
  `ToolRegistry`; model calls become proposals and flow through `BoundedAgentOrchestrator`,
  ToolGate, confirmation UI and `DeviceToolExecutor`/Shizuku. Denials are observations.
- `.litertlm` files are imported through Android's document picker after an operator supplies
  their SHA-256. Catalog inspection requires a text model with CPU support. Cloud is an explicit
  ONLINE mode; AUTO does not silently select it. The legacy expert interpreter is separately
  labeled USER-ONLY in MainActivity.
- `docs/LOCAL_FIRST_PROTOTYPE.md` records the safety boundary, import process, CI evidence and
  Fold-device work still outstanding.
- The prior green GitHub Actions run `38332404439` validates commit
  `25d2bf1b059d0664880821de73ef83ed449ac75a`, before this receipt/local-first implementation.
  Current changes require a fresh green Android workflow and APK artifact; no physical-device
  inference or Fold validation is claimed.
- The available standalone Kotlin compiler compiled the receipt, ToolGate and cognitive-agent
  sources; a deterministic smoke check passed receipt round-trip, success replay suppression,
  STARTED ambiguity blocking and raw-shell rejection. `node --check backend/src/server.js`
  and `git diff --check` passed.
- Local Gradle unit tests/build still cannot resolve Android Gradle Plugin 8.10.0 in this
  sandbox. The updated PR-head Android workflow is the required full validation.
