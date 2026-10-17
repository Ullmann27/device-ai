# CRE1 benchmark-gated promotion (17 October 2026)

## Status

Implemented in PR #19 after baseline 6609c35b4853eaf7d1110e413b992233ba0530fa.
This is a **runtime promotion mechanism**, NOT a claim that CRE1 has passed
an independent benchmark or that the production chat has been activated.
Without a verified local manifest, every dialogue remains SWB1-owned;
CRE1 remains a shadow scorer. Training \`steps > 0\` cannot promote a model.

Key paths:
- \`app/src/main/java/at/heinz/deviceai/learning/Cre1PromotionGate.kt\`
- \`app/src/main/java/at/heinz/deviceai/learning/ContinuumCognitiveRuntime.kt\`
- \`app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryDialogue.kt\`
- \`app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt\`
- \`app/src/test/java/at/heinz/deviceai/learning/Cre1PromotionGateTest.kt\`

## Critical invariants

1. The only candidate pool is the set of currently eligible top-3 SWB1 evidence
   hits; revoked, absent, unreviewed or ungrounded source entries cannot be added.
2. Before persistence, the candidate permutation is checked by equality of
   **complete immutable hits** (not IDs alone), no duplicates allowed.
3. Scoring is bounded, finite and cosine-normalized. Missing/unexpected scores
   force a fallback to the unchanged SWB1 order.
4. Reordering is vetoed on changed numeric tokens, negation tokens, named
   entities/capitalized German nouns and grounded question tokens. This is
   conservative, but it does **not** prove semantic entailment.
5. The selected evidence order is saved in the dialogue turn and returned by
   \`replyWithEvidence\`. Visible text, memory IDs and follow-up provenance agree.
6. Model and audit hashes are checked; corrupted snapshots and malformed
   promotion properties cannot activate the experimental reranker.
7. Nothing in this gate writes owner facts, trains production weights,
   changes the ToolGate, performs web requests or modifies Git's main branch.

## Promotion manifest (internal research protocol)

App-internal path: \`filesDir/learning-core-v2/cre1-research/cre1-promotion.properties\`

Exactly these Java Properties keys are accepted:

\`\`\`properties
schema=1
modelSha256=<64 lowercase hex characters>
modelSteps=<actual CRE1 model training steps, at least 25>
auditSha256=<64 lowercase hex characters, not all zeroes>
sampleCount=<at least 200>
negativeCount=<at least 50>
swb1Correct=<integer 0..sampleCount>
hybridCorrect=<integer 0..sampleCount>
baselineFalseAttributions=<integer 0..sampleCount>
hybridFalseAttributions=0
negativeFalseAnswers=0
pairedPValue=<0..0.05>
fusionWeight=<0.05..0.5>
\`\`\`

Additionally, the validated hybrid must correctly answer at least 5
percentage points more positive holdout cases than SWB1. Score comparison is
restricted to the same frozen lexical candidate pool. The split, model,
scoring rule, and calibration weight must be frozen **before** the sealed test
set is evaluated.

**Important limitation:** The manifest is plain text and has no signature.
Matching hashes verify identity, not that a real evaluation occurred. It must
be generated only after an independently reproduced sealed evaluation.
Do not manually invent metrics or enable the gate from a sample manifest.
No default manifest or automatic promotion writer is included.

## Required evidence before declaring CRE1 active

- A reproducible benchmark implementation and immutable holdout fixture
  with ground truth independent from both encoder training and hyperparameter
  selection.
- SWB1 vs CRE1 paired positive/negative outcomes, stored without publishing
  personal memories; actual p-value computation and model/output fingerprints.
- Baseline/experimental confusion matrices; zero added incorrect user
  attribution on the chosen holdout. Treat zero observed errors as a bounded
  sample result, **not** a general zero-hallucination guarantee.
- Run all relevant Kotlin tests and Node integration contracts locally,
  then \`./gradlew :app:testDebugUnitTest\` and \`./gradlew :app:assembleDebug\`.
- Re-test revoked facts, names, digits, negation, follow-up turns, reboot,
  corruption, interrupted training and an absent promotion manifest.
- Pilot with a rollback path and latency/memory measurements on Android.

Latest GitHub Actions jobs have historically been marked failed with **zero
steps**, so CI metadata alone cannot prove compilation, test failure or test
success. A fully executed local checkout is the outstanding verifier.
