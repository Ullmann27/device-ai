# Implementation Progress

**Current phase:** Continuum Phase 1 foundation on top of the Part 5 local-inference stack.
The Continuum work is stacked on PR #6 and is not approved for merging independently.

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
| QUICK/STANDARD/DEEP research budgets, plans, direct-source discovery and bounded fetch | IMPLEMENTED — current UI uses direct HTTPS seeds; no general search provider is configured |
| Research evidence graph, gap/falsification follow-up, source trust snapshots and hostile-page boundary | IMPLEMENTED — deterministic lexical evidence matching remains heuristic, not calibrated |
| Versioned dossier/checkpoint persistence and URL/hash replay avoidance on resume | IMPLEMENTED — bounded excerpts persist; full page bodies do not |
| AgentActivity opt-in research selection and local text-only synthesis | IMPLEMENTED — ordinary LOCAL/AUTO chat remains web-silent; live network/model use is unverified |

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
- GitHub Actions run `38341592364` passed backend syntax, `:app:testDebugUnitTest`,
  `:app:assembleDebug`, and APK upload on commit
  `adf399432dd43c4c37e39ac633d0eb58ee35dc71` (artifact
  `DeviceAI-v0.3-debug-apk`, id `11787331710`). This validates the receipt/local-first branch
  before the Deep Research engine changes, not commit `1550c533`.
- The available standalone Kotlin compiler compiled the receipt, ToolGate and cognitive-agent
  sources; a deterministic smoke check passed receipt round-trip, success replay suppression,
  STARTED ambiguity blocking and raw-shell rejection. `node --check backend/src/server.js`
  and `git diff --check` passed.
- Local Gradle tasks still cannot resolve Android Gradle Plugin 8.10.0 in this sandbox; the
  current research changes need a fresh PR-head Android workflow run and APK artifact.

## Deep Research engine
- Added bounded QUICK/STANDARD/DEEP plans, a planner adapter contract, discovery abstraction,
  canonical HTTPS URL handling, deduplication, domain fan-out, byte/time/redirect/source/claim
  budgets, and cooperative cancellation.
- Added evidence graph nodes/links for claims and sources, explicit support/contradiction/
  duplicate/unknown relations, explainable evaluated-vs-unknown trust snapshots, applicability
  date/version metadata, gap analysis and bounded counter-evidence follow-up. Lexical matching is
  explicitly heuristic and does not claim calibrated probabilities.
- Added text-only synthesis contract and hostile-source prompt boundary. The AgentActivity Web
  action requires explicit QUICK/STANDARD/DEEP opt-in; model proposals from synthesis are
  discarded. Ordinary local chat does not contact the web. No general search provider or
  credential is configured; direct HTTPS seed URLs are supported.
- Added schema-versioned, bounded, SHA-256-protected atomic dossier/checkpoint persistence.
  Resume requires matching state and skips completed canonical URLs; saved evidence is limited to
  bounded excerpts, not full downloaded documents. Long-term research memory requires explicit
  selection, verified claims, independent sources, and MemoryWritePolicy approval.
- The standalone cognitive source compile and 11 deterministic research-engine tests passed
  using temporary test/API stubs. Backend syntax and `git diff --check` passed. Android Gradle
  tests/build cannot run locally because plugin `8.10.0` cannot be resolved. Workflow run
  `38349576117` for commit `1550c53346e8f375f9d9948eb2098b74104310f6` ended
  `action_required` with zero jobs, so no fresh Android result or APK artifact exists; repository
  workflow approval is required. No live-network, imported-model, physical-device, Fold-layout,
  or device-cancellation validation is claimed. See `docs/DEEP_RESEARCH_ENGINE.md`.

## Continuum Phase 1 foundation
- Implemented version-1 `ContinuumSeed`/`AiSeedKernel`, typed owner public-identity reference,
  authorized-node identities, strict current-version rejection, deterministic encoding, SHA-256
  binding, and a `SignedContinuumSeed` envelope using the existing `IdentitySigner` interface.
- Implemented canonical content-addressed object IDs and state roots plus a bounded immutable
  file store with integrity verification, metadata binding, atomic replacement, path validation,
  and app-private storage as its intended deployment boundary.
- Implemented bounded context compilation with explicit privacy filtering, deterministic priority,
  credential-pattern redaction, and untrusted-evidence delimiters. Hybrid-memory results can be
  supplied explicitly; the compiler does not start research.
- Implemented the typed strategy selector, hard budget enforcer, cancellable/deadline-bounded
  data-only candidate/critic/verifier/judge orchestration, and a provider-neutral local compute
  registry/scheduler with owner authorization, manifest/result verifier seams, expiry, revocation,
  and bounded task/result metadata.
- Implemented Cognitive RAID policy/planning metadata, the ten-family typed benchmark corpus
  (support/citation coverage, contradiction detection, tool proposals, calls, harness latency,
  context size, correction, and recovery metrics), and an abstraction-level model-continuity
  harness.
- AgentActivity can opt into the context adapter only through
  `CONTINUUM_CONTEXT_COMPILER_ENABLED`; this build flag defaults to `false`. Deep Research remains
  explicitly opt-in, device actions still pass through ToolGate, and no compute node is enrolled
  automatically.
- Fourteen focused Continuum tests passed in a standalone Kotlin/JVM test run with temporary
  dependency/JUnit stubs. Backend `node --check` and `git diff --check` passed. This is useful
  source-level evidence, not the Android Gradle test result.

### Continuum validation status
- **IMPLEMENTED:** the source-level foundations listed above; tests are checked in.
- **CI VERIFIED:** no CI result for the new Continuum changes is claimed. Android workflow run
  `38352336772` for the implementation commit ended `action_required` with zero jobs, zero failed
  job logs, and zero artifacts; it did not execute tests or build an APK. The inherited baseline
  on commit `3923aac` succeeded in run `38349968863`, but that predates the Continuum code.
- **NOT YET DEVICE VERIFIED:** Android Keystore signing for a Continuum seed, physical-device
  inference with the context flag enabled, persistence behavior on device, and real compute-node
  execution have not been tested. The context feature flag remains off by default.
- **FUTURE:** encrypted replicas, internet/object storage, remote compute/network listeners,
  distributed scheduling, automatic recovery from a replica, and any physical battery/thermal
  claims. No Astra/frontier capability claim is made.
- In this sandbox, `gradle :app:testDebugUnitTest` fails during plugin resolution because
  Android Gradle Plugin `8.10.0` is unavailable; it did not reach Kotlin compilation or test
  execution. `:app:assembleDebug` and the requested APK artifact therefore remain unverified here.
  The repository Actions workflow must validate the committed Phase 1 changes.
