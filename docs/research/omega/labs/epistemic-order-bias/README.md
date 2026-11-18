# HypothesisWorkspace: order-bias pilot (2026-11-08)

STATUS: RESEARCH_ONLY / ISOLATED / NOT_FOR_CODE50. No production code, no model weights, no Android tests, no APK.

## Actual source and environment
- Original file: app/src/main/java/at/heinz/deviceai/cognitive/HypothesisWorkspace.kt
- Source commit: 8bc695fd0d0fcc10ef0aa88684da8b57acc928f4
- Original Git blob SHA-1: 916b21b7d1d3e8c05771c7f4250d6e085e6f063f
- Locally verified: the Kotlin source copy used for the host pilot has the exact same Git blob SHA-1.
- Compiler: kotlinc-jvm 1.9.0; JRE 21; synthetic host-only. This is not Android QA.

## Hypothesis and negative control
Four hypotheses enter sequentially with identical initial weights 1.0. Their normalized weights should be invariant under the 24 insertion permutations IF the caller intends equal priors. The controls are a normalized total of 1.0 and an ideal batch-normalized vector of four times 0.25.

The production algorithm normalizes after each individual add. The pilot compiles that unmodified class and enumerates all 24 orders; four equal-cost, zero-thermal probes test whether order influences the next experiment selected.

## Reproduction from repository root
    kotlinc app/src/main/java/at/heinz/deviceai/cognitive/HypothesisWorkspace.kt docs/research/omega/labs/epistemic-order-bias/HypothesisWorkspaceExperiment.kt -include-runtime -d /tmp/continuum-hypothesis-lab.jar
    java -jar /tmp/continuum-hypothesis-lab.jar

## Measured result
- 24/24 insertion orders: the most recently added hypothesis receives the highest weight.
- Forward: config=.125, data=.125, lifecycle=.25, ui=.50.
- Reverse: ui=.125, lifecycle=.125, data=.25, config=.50.
- Maximum within-vector weight spread is .375; total always 1.0.
- Probe selected follows favored late hypothesis; 24 permutations distribute among four probe IDs 6/6/6/6.

## Interpretation and guardrails
This establishes a **synthetic order-dependence under equal input weights**, not a failure in the live app. HypothesisWorkspace is currently a research module without verified production wiring. The input weights are explicitly heuristic, not calibrated truth probabilities. If sequential admission intentionally means a fresh prior shift, document that contract; do not automatically change behavior.

Candidate future comparisons: batch admit then normalize once; alternatively retain raw unnormalized weights internally and normalize only at query/observation. Test permutation invariance, validity bounds, bucket limits, numerical stability, zero/low cost, misleading probes and existing regression behavior. Owner authorization and separate RED→GREEN proofs precede production integration. No agent autonomously chooses an external tool based solely on this module.

Android, Gradle, real-device, real-model training, GPU and release signing were NOT_RUN.
