# Continuum JARVIS: compatibility and staged integration (research)

Reference baseline: PR #265, `claude/m7-m8-integration-kxrxy2`,
commit `c863bcbef55911fd1e71b14487193870cb049f7d`.

This document is an integration **plan**, not proof of a working end-to-end
release. No research branch is automatically a production dependency.

## Preserve the neuro-inspired project

Keep the existing temporal, memory and decoder research as independent,
measurable components: `ContinuumCognitiveRuntime`, `Cre1ResearchLab`,
`ContinuumDecoderBridge`, `ContinuumAutoDialogueResearch`,
`ContinuumBrain` and the SWB/EWM paths. Keep existing shadow/owner switches,
research isolation and baseline-vs-experiment comparisons. Neither a textual
working-state summary nor a structured graph is a replacement for neural
state or proof of improved model reasoning.

Reuse rather than duplicate `SelfModel`, `ExperienceCompiler`,
`MemoryEcology`, `MemoryGraph`, `AiContextCompiler`,
`LocalModelReasoningAdapter`, `ComputeFabric`,
`DevelopmentCoordinator`, `DeviceDevelopmentBacklog` and
`BoundedAgentOrchestrator`.

## Compatibility ledger

| Research work | Intended benefit | Acceptance gate |
| --- | --- | --- |
| PR #280 | Optional, unverified past-agent experience hints; negative warnings | Same-build tests, hostile-history tests, actual LLM A/B; default OFF |
| PR #281 | Remove stale and contradicted records from current-answer context | Tests for historical queries and owner-fact revocation |
| PR #282 | Compute authorization lease covers the complete task duration | Registry clock, expiry, remote-result verification |
| This PR | Typed owner-origin event -> durable PROPOSED development backlog entry | Owner trust boundary, dedupe, invalid input, NO execution or acceptance |

Do not cherry-pick changes solely because their source branches compile
individually. Verify API compatibility against **one exact merge-base and
resulting combined tree**. Re-run every impacted Kotlin/Android/Node test
and the target-device smoke test on that resulting commit.

## Living JARVIS event contract

`JarvisIntentIntake` is an optional **data-only** boundary:

1. A trusted Android ingress provides an event origin and an approved ID.
   The LLM does not get to label its own output `OWNER_TEXT` or
   `OWNER_VOICE`.
2. Owner-originated entries require an independent app-owned verifier;
   the default rejects them. Passive device diagnostic proposals
   additionally require explicit opt-in (default OFF).
3. Untrusted external notifications cannot create tasks in v1.
4. A validated event becomes exactly one `DevelopmentBacklog` task in
   **PROPOSED**, keyed by a stable event ID and checked for conflicting
   content on replay.
5. Existing owner UI/approval must separately move the task to READY.
   No call to `BoundedAgentOrchestrator.run`, `ToolGate` or remote
   compute occurs during intake.
6. The existing backlog's authorized local worker, sandbox, test evidence,
   repair review, signing and update path must be separately integrated
   and checked. Nothing here issues a code-change permission.

This branch does **not** wire a sensor listener, speech recognizer,
notification listener or Android UI button. Those need explicit trust
provenance and device tests; never silently connect raw Android events to
execution.

## Validation and APK entry gates

* Local JVM tests for event rejection, consent, identical replay,
  conflicting replay, storage persistence and PROPOSED/READY boundary.
* Full `testDebugUnitTest` plus Node contract tests on the combined commit.
* Trust-boundary integration: verify owner gesture is real; no model output,
  accessibility notification or network string may forge the owner origin.
* Provenance and temporal tests across EWM and active owner-fact retrieval.
* A/B studies before enabling adaptive planning, experience promotion or
  compute offload; verifier success != independently correct strategy.
* Fresh signed APK on the target Fold7: install/update, model load and
  rollback handling, memory migration, crash/ANR, battery and thermal gates.
* Preserve signed owner updates, latest installed APK's package identity,
  storage and settings; no untested release or automatic merge to main.

No full Gradle or Android hardware pass has been claimed for this branch.
