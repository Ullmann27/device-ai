# Continuum research integration: next APK acceptance ledger

**Baseline**: PR #265, commit `c863bcbef55911fd1e71b14487193870cb049f7d`.
**Integration starting point**: PR #280, commit `b5c6caa2bf5b4263c79086ef1fa414f646d2e37b`.
**This branch is not a release branch.** The user's existing signed APK must remain functional and unchanged until independent owner/device verification.

## Preserved core, not replaced

The bio-neuronal/time-dependent research work, SWB/EWM paths, CRE1, decoder research,
`ContinuumCognitiveRuntime`, `ContinuumBrain` owner-controlled OFF/SHADOW/ON,
`MemoryEcology`, `SelfModel`, `ExperienceCompiler`, and the existing local-model
and signed-update paths are not rewritten by this integration.

The code does not claim a particular proprietary frontier-model architecture,
latent recurrence, true autobiographical consciousness, or training gain.
A bounded textual summary cannot replace a neural recurrent state.

## Selected, conflict-checked source integrations

1. PR #280: bounded read-only historical hints (2 successful + 2 failed episodes);
   treated as unverified, opt-in, never as new permissions or owner facts.
2. PR #281: filter STALE/CONTRADICTED records out of current-answer context;
   no deletion of historical evidence from the underlying memory store.
3. PR #282: reject compute node leases too short for the permitted task runtime;
   no unmeasured RAM-is-speed heuristic or new networking transport.
4. PR #283: optional event intake -> existing persistent DevelopmentBacklog
   **PROPOSED** state; never autonomously ACCEPT, claim or execute a tool.
5. `VerifiedSkillReview`: fail-closed independent evidence oracle in front of
   the existing `ExperienceCompiler`, which still produces *owner-review
   candidates*, not installed/activated skills. There is not yet a trusted
   production implementation of this independent verifier.

These are additive research changes, not proof of integration into the UI,
speech, Android sensors, model runtime or local development workstation.
No new Android permissions are declared.

## Evidence and release blockers

- Compile/test the **combined tree**, not just individual topic branches:
  `./gradlew testDebugUnitTest` (or the verified project's configured flavor
  command); check all impacted Node contract tests, AGP lint and signing.
- Verify new `JarvisIntentIntakeTest`, `VerifiedSkillReviewTest`,
  `AgentExperienceRecallTest`, `AgentExperiencePairedBenchmarkTest`,
  `AiContextCompilerTest` and `ComputeFabricTest`.
- Test hostile event origins, replay, tampered evidence, source revocations,
  expired compute grants, data privacy and resumed side effects.
- The owner event verifier is only an interface. Do not wire to UI until its
  trusted provenance and consent ceremony have been independently tested.
- Neither `ComputeFabric` networking nor end-to-end remote inference is added;
  external hosts require transport, authenticating certificates, privacy
  controls, cancellation and independent result verification.
- Test real .litertlm model on Fold7 with benchmark inputs and thermal/RAM
  instrumentation. Mock unit tests do not prove increased intelligence.
- Preserve the installed standalone owner's applicationId, versionCode
  monotonicity, *identical* signing certificate, persisted user files,
  model storage, owner approvals and updater behavior.
- Produce and verify a **new signed APK** only after a reproducible, all-green
  combined build; do not distribute QA/debug-signed artifacts as an in-place
  update. No APK created by this branch yet.

## Explicit promotion rule

Development backlog remains:
`PROPOSED → (owner accept) READY → IN_PROGRESS → VERIFYING → (independent
executed tests) DONE`.
An LLM, event origin string, high evaluator score, local-tool output digest
or repeated success entry cannot substitute for these transitions.

Skill distillation remains review-only, requiring external independent receipts
and owner approval; historical experiences remain separate from verified owner
facts. Successful training, performance advantage and full autonomy are
hypotheses pending measurement.

## Required next actions before APK

1. Assemble a clean combined source tree and run JVM/Node/Android tests.
2. Resolve any compatibility/build failures at exact commit SHA on an isolated
   branch; do not alter main or existing signed releases.
3. Hook trusted owner UI intake *after* its provenance negative tests pass.
4. Benchmark the local reasoning adapter with/without historical hints,
   baseline and phased context on identical tasks; freeze model and corpus.
5. Only after green device checks, build an owner-signed Continuum APK with a
   higher versionCode, verify certificate equality and test update/rollback.

## 31.10.2026 — Mandatory Frontier/JARVIS checkpoint, not a release

Read [canonical state](../../docs/CONTINUUM-CURRENT-STATE-20261031.md), [Frontier handoff](../../docs/handoff/CONTINUUM_FRONTIER_COMPUTE_20261031.md), [PR #287](https://github.com/Ullmann27/device-ai/pull/287) **before** release preparation.

- Parent chain of PR #287 is #265 → #284 → #285; #286 is **parallel** signing research, #263 JARVIS owner-video evidence separate. Do not silently combine these branches.
- Synthetic benchmark: 3,000 different output tasks share 30 immutable heavy stages; 43/43 isolated V8 compatibility checks. NOT a confirmed Node/Android/real-brain benchmark or new GPU capacity.
- Preserve CRE1/EWM-v2/SWB, temporal/bio-neural research, actual `AgentActivity`/ToolGate, signed updater, owner memory and existing models.
- Last device report: **0.3.28, versionCode 29**, `at.heinz.deviceai.continuumlocal`. Any proposed **code29** release (including parts of PR #286 scripts) is not a higher in-place update; verify installed PackageManager and build **code ≥30**, same original certificate and pinned update host. The existing Render static gates do **not** publish a correctly signed APK.
- Release gating: native Node + full Gradle/Kotlin/lint on combined tree; real-model M8 RED→GREEN; Fold7 package/version/signing/update button, persistence, voice, thermal, memory and rollback acceptance. No signing credentials in GitHub or logs.

