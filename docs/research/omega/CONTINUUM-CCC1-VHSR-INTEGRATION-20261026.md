# Continuum intelligence research – CCC1 / VH-SR / feedback junction

Date: 2026-10-26
Repository: Ullmann27/device-ai
Work branch: chatgpt/continuum-feedback-process-safety-20261026
Stack base: PR #175 (feedback), which itself builds on the v25 source proposal.
Related research: PR #133 (VH-SR verifier) and PR #169 (CCC1 independent checks).

## Implemented as real source

1. research-evidence-gate.mjs: bounded research-only acceptance of exact code/task/source identities, complete RED/GREEN/fresh test-case IDs, skipped-test parity, and existing bounded counterexample-verifier PASS.
2. research-repair-bridge.mjs: typed caller actually invokes validateCcc1CandidateBinding, runIndependentCcc1PatchVerification, canonical hashing and runCandidateVerifier. CCC1 continues to require original owner grant, branded OS executor, strict allowed file scope and unchanged test digests.
3. research-evidence-gate.test.mjs: native Node unit tests, including negative falsification and immutable output.
4. research-repair-bridge.test.mjs: true-repository guard regressions for invalid patch hashes and unbranded/no executor; full native execution on exact committed source remains to be run.
5. Existing ResearchFeedback now uses the previously committed multi-process JVM/file lock; its synthetic research trainingFingerprint can be passed as DECLARED metadata only, never authenticated owner consent.

## Actual measured research

Local Node.js v22.16.0, isolated research module/test source: **10/10 tests passed, no failures**. One test evaluates 13 deliberately mutated CCC1 receipt fixtures against two controls:
- Simple status-only gate incorrectly accepts 12 of 13 manipulated specimens.
- The strict research gate accepts 0 of 13 manipulated specimens.
- A properly formed synthetic RED/GREEN/fresh + VH-SR-PASS specimen results only in RESEARCH_REVIEW_REQUIRED, with learningEligible=false, autoApply=false and ownerReviewRequired=true.
A tenth metamorphic test verifies legitimate test-order permutation, additional executed test cases and matching skipped-test hashes remain reviewable, while mismatched skipped-test identities are blocked. These are deterministic synthetic mutations, deliberately chosen to exercise new checks. They do not measure real model accuracy, code-repair success or independent adversarial generalization.

New Kotlin feedback lock helper was recompiled locally with kotlinc and Java 21; two actual separate JVM child processes serialized critical sections, exception release and symlink-root refusal: smoke checks PASS. The complete updated Kotlin feedback/JUnit tests are NOT_RUN here.

## Safety, provenance and hard exclusions

This path never merges, signs, installs, executes arbitrary candidate shell commands, changes ToolGate, writes a repair ledger, stores owner memories, authenticates a model-generated callback, or promotes weights. Its result is a research review ticket even after both evidence sources pass. An attacker may manufacture a JSON result or a feedback hash: they remain unauthenticated and cannot authorize learning. The experimental verifier callback must only originate from a trusted worker; never pass model output as an executable callback. The existing CCC1 runner performs the genuine bounded OS-isolated checks when the owner provides its canonical grant and executor; the present host did not run it on this new branch.

The runResearchRepairBridge function is opt-in and has no autonomous background scheduler or Android UI caller yet. It represents a real code-level module-to-module integration without automatically enabling new privileges.

## Remaining required acceptance gates

- Run exact-HEAD native Node suite including new bridge tests and all prior CCC1/repair integration tests; compare results to baselines.
- Run updated Kotlin/JVM full source graph; test separate review processes, restart and conflict cases against real FileLearningExperienceStore.
- Real owner-granted Docker RED→GREEN→fresh-GREEN candidate with trusted VH-SR counterexample suite, sealed unseen tests and negative controls, preserving case IDs.
- Design an authenticated, consent-bound owner review API before any real feedback can influence task learning. The research fingerprint alone is NOT consent or provenance.
- Compile Android with SDK, validate 0.3.24 source inventory, Fold7 native Gemma dialogue, thermals/RAM and package/certificate continuity before a new owner APK. Frozen v25 source gate must keep rejecting unattributed research files.

## Rollback

Leave this draft unmerged or revert the research module/test/documentation commits. The original CCC1 and VH-SR entrypoints and existing Android app are unchanged; no new production state migration.
