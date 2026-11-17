# CCC1 / memory repair audit — 2026-11-03

Source reviewed: private `Ullmann27/device-ai`, pinned PR363 commit
`7dacf0fb8f21e0dec9957fec23d8edb7feea3e77`; local branch
`repair/continuum-shizuku-composer-20261103`. EXP-02/05/07/08/11/12.
This is local source/test evidence, not Fold7, model-quality or release acceptance.
Remote state: `LOCAL_ONLY / NOT_PUSHED` at authoring; root agent owns publication.

## Proven defects and bounded repairs

1. `HybridMemoryRetriever` admitted unrelated private memories solely because a
   grammar word matched. With the owner fact “Meine Schwester ist Ärztin in Wien.”,
   the question “Was ist Photosynthese?” selected the whole fact through “ist”.
   `AiContextCompiler` then included it in the local-model prompt. Two new real
   Gradle regressions failed before the implementation change, recorded in
   `/workspace/repair-evidence/gradle-baseline.log`. The repair excludes German /
   English grammar words from query scoring only. The literal evidence, stored
   facts, SWB1 retrieval, semantic callback and owner confirmation rules are unchanged.
   Added controls preserve a unique topic/entity match and reject topicless questions.
   Post-repair Gradle verification is owned by the root run; do not infer GREEN
   from this source note alone.

2. `scripts/continuum-typed-coding-bridge.mjs` accepted a consistently reduced
   repair suite: RED 30 executed, candidate 1, independent 1. All evidence and
   command counts agreed, so the existing inconsistency tests missed the defect.
   The new test first failed with the returned patch hash instead of null
   (`/workspace/ccc1-memory-coverage-red.log`). Candidate execution must now be at
   least the RED baseline; the existing independent >= candidate requirement is
   retained. The full bridge test file then passed **15/15**, zero skips
   (`/workspace/ccc1-memory-coverage-green.log`). This count comparison does not
   authenticate test reports or prove semantic correctness of a repair.

The bridge fix and its new regression were captured during local source
materialization in `ac1f25d`; those two paths are repaired inputs and must not be
reported as byte-identical pinned PR363 source. The archived Qwen result fixture
required by the existing test was fetched unchanged from the pinned source at
`docs/evidence/sol-autonomous-coding-20261024/first-model-run/result.json`.

## Actual entry points and capability boundary

- “Baumeister programmiere …” -> `ContinuumWorkerChatRouter.Draft` ->
  `AgentActivity` development goal -> `DevelopmentCoordinator` ->
  `LocalModelReasoningAdapter`. This App-model path returns a bounded review
  proposal and suggested tests; those tests are not executed. Its export action
  is a document export, not a Termux repair dispatch.
- “CODING-AUFTRAG ENTWERFEN” -> explicit commit / paths / acceptance review ->
  `ContinuumCodingTask.Authorization` -> internal builder ->
  `ContinuumTypedCodingBridge` PREPARE/RUN -> fixed Termux bridge ->
  `coding-orchestrator.runCodingTask` -> existing model candidate provider or
  coding-agent-loop -> existing canonical repair/independent verifier.
  This worker requires a separately configured local model service and an
  external owner-controlled isolation configuration. An imported READY Gemma in
  the Android app does not provision that Termux model service.
- The CCC1 repair-chain button uses the existing deterministic repair pipeline.
  Its lexical/probe/disambiguate cores are not evidence of an Android Gemma
  model repairing code. No replacement scheduler, memory database or worker was added.

## Persistence / receipts inspected

Explicit facts use `BootstrapMemoryStore` and the existing learning ledger.
`syncOwnerFacts` does not automatically reactivate revoked facts; an explicit
`confirmOwnerFact` can. Conversation retrieval reselects current evidence.
The signed Continuum runtime archive persists memories/experiences separately.
The durable tool receipt store rejects ambiguous dispatch replay rather than
executing the same call again. Existing focused tests cover fresh-instance
recovery, revocation and uncertain replay; the root full Gradle report is the
authority for their actual result on this combined tree.

## Reproduction commands

```sh
node --test scripts/continuum-typed-coding-bridge.test.mjs
gradle :app:testDebugUnitTest \
  --tests at.heinz.deviceai.cognitive.HybridMemoryRetrieverTest \
  --tests at.heinz.deviceai.cognitive.AiContextCompilerTest
```

Run the repository's full Node and Android suite on the final combined commit.
The first broad Gradle RED run reported 1698 tests, 4 failures and 3 skips: the
two new memory assertions plus two local-model lifecycle tests blocked by a JVM
class-version mismatch. Those additional failures belong to root/local-model
verification and must remain visible in the final report.

## Hardware / worker blockers and rollback

The host exposes Docker CLI/socket as uid1000, with runsc and runc registered;
the audit's read-only inspection initially found no local images. That is
capability discovery, not evidence that a sandbox workload executed. Current
Fold7 Termux provisioning, app-to-worker completion, native model generation,
RED/GREEN repair and process-death callback behavior still require real device
receipts. No signing, installation, model promotion, owner-data deletion or
remote publication was performed by this audit.

Rollback is limited to the HybridMemoryRetriever scoring edit and the typed
bridge candidate-count comparison plus their new regressions. Preserve all
existing fact/receipt files and the original test sources.
