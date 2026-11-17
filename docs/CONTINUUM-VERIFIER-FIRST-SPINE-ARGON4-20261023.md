# Verifier-first cognitive spine: Argon 4 review and safe scope

Date: 2026-10-23. Base: PR #94 source commit `0499e2a0`; branch remains isolated.
Relevant existing source:
- Android: `AgentActivity.kt`, `LocalModelHost`, `LocalModelRuntime`.
- Research: `learning/span/ContinuumResidualSpanCore.kt` provides `predict(evidence,question): Prediction(answer,start,end,confidence,candidates)` **but is not a certified truth verifier**. Current CSX3 is a bounded research model.
- Research: `learning/span/ContinuumCounterfactualCurriculum.kt` requires a `ReviewedFact` with original answer, replacement and independently reviewed negative evidence. It cannot infer these from a single free-text 'Merke dir' input.
- CCC1: `.github/continuum/ccc1-cli.mjs` implements `compile --project --goal`, `submit --project --state --goal`, `status --state --format json`, `run-once --project --state`, `loop --project --state --cycles`. Argon's proposed `cycle --state-dir --trigger` DOES NOT EXIST.

## What is implemented here

`ContinuumCognitiveSpine.kt` is a **pure Kotlin, side-effect-free** source quotation guard, not a general intelligent chat. A typed `SpanProposalPort` returns source offsets. It accepts one explicitly owner-marked source only after a matching SHA-256 check and bounds checks. The text is returned as a literal quotation with an explicit warning that semantic correspondence remains unverified. It abstains on missing, ambiguous, unreviewed, mismatched or malformed evidence and exceptions from the port.

Memory and coding commands return `OWNER_CONFIRMATION_REQUIRED` without writing memory, generating training data, launching Node, repairing files, altering weights, or invoking models. The UI must provide an explicit user-selected task type; magic words in a free-form question NEVER grant privileged execution.

The result `receiptDigest` is a repeatable SHA-256 fingerprint of the displayed result only, **not** a tamper-proof signed journal or verified execution receipt.

## Why this differs from Argon's draft

1. Exact span inclusion, detecting new numbers and matching negation words does **not** prove semantic truth. Any claim of zero hallucinations is unsupported.
2. `confidence` from a research model is not a calibrated/certified verification threshold. Source SHA-256 guarantees exact bytes relative to the caller's pin, not publisher authenticity or factual correctness.
3. `Merke dir` cannot claim stored data or synthesize reviewed negative evidence; use the existing owner-confirmed store and independent curriculum review.
4. An Android lambda that accepts fabricated worker outputs cannot prove a Termux execution bridge. Do not report CCC1 cycles as completed without journal and verifier receipts.
5. Reusing code17 builder scripts to publish code18 risks replacing an APK without correct version/signature/archive gates.

## Real local test

Using local `kotlinc-jvm 1.9.0` (JDK 21), compiling `ContinuumCognitiveSpine.kt` + `ContinuumCognitiveSpineChecks.kt` and executing the resulting JAR succeeded. The executable checks cover valid source quotation, rejected forged hashes, review flag, multi-source ambiguity, mismatched IDs, invalid offsets, model exceptions, memory confirmation and coding confirmation. The JUnit wrapper is supplied for `:app:testDebugUnitTest` but this complete Android Gradle task has **not** been run.

## Stages not yet delivered

- A verified adapter reading source segments from the existing local memory with explicit owner approval, correctness tests and passage provenance.
- A separately evaluated adapter to `ContinuumResidualSpanCore.predict` with independent held-out labeled examples, abstention calibration and contradiction tests. No automatic promotion.
- A real Android↔Termux CCC1 status/approval bridge backed by a trusted transport, exact CLI argv, real receipts, cancellation, lifecycle handling and OS confinement.
- Controlled Android UI opt-in routing preserving existing chat/default model path. No silent model substitution.
- Combined Node/Gradle/lint, clean APK build with code > 17 and old signer, hash/archive checks, Samsung Fold7 testing and explicit release approval.

No production branch, signing material or owner model weights are changed.
