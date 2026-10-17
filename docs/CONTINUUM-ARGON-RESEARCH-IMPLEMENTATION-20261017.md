# Continuum · Argon research implementation checkpoint (2026-10-17)

## Evidence and provenance

Research input: the owner's rescued partial *Gemini Argon* response. Verified baseline PR #19 head: `66d62f1e3788fbd4f672eeb47db50422bbcd7bfe`. The content is an external hypothesis, not proof that a bug is still present in the current checkout. This checkpoint records new, reviewable source changes on the development branch only; no merge to `main`.

## M1: Evidence-conditioned, from-scratch language experiment

Changed sources:
- `app/src/main/java/at/heinz/deviceai/learning/TinyContextDecoder.kt`
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumDecoderBridge.kt`
- `app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt`
- `app/src/main/java/at/heinz/deviceai/AgentActivity.kt`
- `app/src/test/java/at/heinz/deviceai/learning/ContinuumDecoderBridgeTest.kt`

The legacy `trainExperiment()` implementation still trains with one constant `encoder.representation("research context")` vector. **It is intentionally kept for comparison, not silently relabeled as repaired.** The newly added **separate paired path** addresses the train/inference mismatch:

1. `DecoderEvidencePair(evidence,question,answer)` uses one canonical `decoderEvidencePrefix()` for both training and generation.
2. Teacher forcing predicts **only the answer bytes and EOS** following that prefix, not the prompt bytes. This removes the prior sequence-prefix mismatch in addition to the conditioning mismatch.
3. Each example obtains an independent 16-dimension conditioning vector from `TrainableSubwordEncoder.representation(evidence+"\\n"+question)`.
4. `trainEvidencePairs()` and `evaluateEvidencePairs()` use exactly the same per-example conditioning rule. `experimentalPairedContinuation()` receives the same evidence+question representation and canonical prefix.
5. Train and audit inputs must be different, even when answers are rewritten. They are restricted to bounded, single-line synthetic research cases in the Android demonstration.
6. Separate `context-decoder-paired.cdx` checkpoints prevent earlier, unpaired weights from being mistaken for aligned paired training.
7. Candidate weights are persisted only if training improves, audit does not deteriorate, the run completes and cancellation has not been requested. `pairedStatus().usableForChat` remains **false**.
8. The Android **Lernzentrum → CDX1 · BELEG + FRAGE → ANTWORT (LABOR)** action performs one explicit, bounded synthetic experiment. It reports losses, accepted/rejected status, model checkpoint and *untrusted* generated sample text when a model is accepted. No owner facts or web content are fed into the training corpus.

**Important:** A 7,746-parameter byte-level MLP cannot be expected to converse generally. An audit with a few toy sentences does not constitute a public-quality release gate. The decoder is now *research-executable* through the Android UI but **not productively activated as the chat answer authority**. There is no claim of zero hallucinations.

## M2: Repair candidate priority safety rule

Changed files: `.github/continuum/repair-loop.mjs` and `repair-loop.test.mjs`.

`rankCandidates()` first sorts candidates by fixed risk tiers:
- Tier 0: targeted diagnostic fixes / precise edit replay
- Tier 1: single-hunk revert
- Tier 2: whole-file revert, always last

Laplace-smoothed learned experience still ranks strategies **within the same tier**. The added test supplies a deliberately misleading high-success ledger for whole-file reversions and checks that a minimal typo fix is chosen first. This does not make a Git worktree an operating-system sandbox and does not prove that any historical failing E2E test has been repaired.

## Required acceptance checks (not claimed to have passed)

```bash
node --test .github/continuum/repair-loop.test.mjs
node --test .github/continuum/teamleader-end-to-end.test.mjs
node --test .github/continuum/autonomous-teamleader.test.mjs
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The connected GitHub Actions results for the new head currently report failed jobs **without executable steps** (e.g. run `38993938813` and regression run `38993866539`). Those are not evidence of a source compilation error or a green test result. A real local checkout/runner must still compile, test and install the APK. A connected GitHub file diff review and presence checks are not equivalent to compiling Kotlin or executing the E2E suite.

## Next gates before CRE1/CDX1 production activation

1. Run Kotlin and Node suites plus Android install/restart. Fix any actual compiler or test diagnostics.
2. Build an independent, sealed, sufficiently sized positive/negative retrieval benchmark (including names, numerals, negation, revocation, paraphrases and OOD). Evaluate SWB1 baseline and CRE1 shadow on exactly the same evidence set, with no train/test leakage.
3. Implement a model-hash-bound CRE1 *promotion manifest* and verify an improvement without a measurable false-attribution regression. A checkpoint's `steps>0` alone is not a promotion criterion.
4. Construct a separate, larger held-out evidence-to-answer corpus. Measure exact answer accuracy, entity/number/negation fidelity, abstention, latency, memory, robustness, and old-vs-paired generalization, not merely training loss.
5. Before showing CDX output as a trustworthy fact: independent evidence entailment/factual consistency checking plus safe deterministic fallback. Simple lexical overlap is not a proof of entailment, so it cannot promise a 0% hallucination rate.
6. Run repair on an actual isolated checkout with meaningful test execution and anti-test-weakening assertions, then add OS sandboxing for truly untrusted input.
7. Keep all changes reviewable. No unverified writes to `main` and no cloud model or paid external API required.

**Current scope:** This is a concrete research integration increment, **not** a fully autonomous self-improving AGI or general-purpose coding model.
