# Continuum research status and release gates

Updated: 2026-10-15. This document records implementation and evaluation
evidence; it is **not** a declaration of generalized intelligence.

## Runtime boundaries

- Productive Android memory dialogue still uses `TrainableSubwordEncoder`
  and `ContinuumMemoryDialogue`, preserving the SWB1 model format.
- Experimental `SWB2` models use separate magic `0x53574232` and
  32,960-byte SHA-256 checksummed snapshots. They are intentionally rejected
  by the production SWB1-only Kotlin model loader.
- Kotlin `Swb2ExperimentalEncoder` and JavaScript `SubwordEncoder`
  perform equivalent weighted encoding, intent updates and semantic training;
  cross-runtime fixtures compare independently trained weight arrays.
- `ContinuumSwb2DecoderResearch` uses its own research checkpoint.
  It is not a production chat route, a ToolGate input or a memory-writing path.
- `trainable-context-decoder.mjs` is a bounded research token decoder with
  an analytical backward pass, explicit checkpoint fingerprint and typed
  token-level constraints. Lower training loss alone does not authorize
  deployment. Natural-language rules must be compiled and independently checked.
- `experimental-hybrid-retrieval.mjs` is NOT the productive retriever.

## Evidence

Node regression and Android CI publish the tests and JSON audits.

- Legacy SWB1 snapshots and JavaScript forward/backward logic are preserved
  by default.
- SWB2 candidate snapshots use different magic and explicit opt-in.
- `swb2-verification.test.mjs` checks finite-difference gradients and a real
  trainable token decoder with grounded vocabulary restrictions.
- `Swb2ExperimentalEncoderTest.kt` plus
  `contrastive-parity.mjs` check JS/Kotlin SWB2 weight agreement.
- The frozen `neural-generalization-audit.mjs` benchmark includes three
  positive paraphrases and two off-topic negatives, on immutable evaluation
  texts disjoint from the experimental training inputs.
- Initial SGD experiment: **0/3** recovered unseen positive holdout questions,
  **0/2** off-topic false positives and protected intent accuracy **10/20**
  before and after. Not eligible for promotion.
- Experimental sparse Adam comparison: training loss fell, but unseen scores
  saturated to roughly **0.90–1.00** even for off-topic questions. The strict
  semantic margin gate therefore still did not promote any of the three targets
  (**0/3**). Do not lower thresholds simply to manufacture a success.

These benchmark examples have now been inspected by researchers. Future
promotion must use **new sealed evaluation domains**, plus a false-positive
ceiling, repeated seeds, verified SWB1 retention and device-level measurements.

## Agent boundaries

`developer-agent.mjs investigate --file <backlog.json> --base <40-hex-sha> --out <report.json>`

The new investigative path only claims already READY `INVESTIGATE` tasks.
It creates an immutable-commit static analysis report in an isolated worktree,
rechecks its reproducibility, runs the fixed Node regression profile, and
records evidence in the persistent backlog. It does not synthesize source
patches or assume name-based static reachability is conclusive.

Automatic `TEST`/implementation patch synthesis, full release-level rule
compilation, and production activation of the grounded decoder are **open**.

## Release requirements

1. Preserve and independently verify SWB1 user checkpoints.
2. Demonstrate multi-seed improvement on untouched and sealed semantic
   paraphrase suites with zero unacceptable false-positive regressions.
3. Verify Android and JavaScript parity after every algorithm change.
4. Test that generated answers are grounded, respect all active rules,
   and do not reach device actions without the existing ToolGate.
5. Confirm device/emulator instrumentation tests, lifecycle and resource
   behavior, Android APK builds and safe fallback to existing chat.
6. Only then approve explicit product migration and reversible rollout.

Never report an untrained or overfit decoder as conversation-ready.

## CRE1 relational retrieval encoder (2026-10-16, experimental, not promoted)

Root causes of the SWB1/SWB2 failure found in the code: all character 2-4-grams
of a sentence hash into 256 buckets (up to 128 active), so unrelated sentences
share many buckets (input-level collapse); `sigmoid(2.5*dot)` of small tanh
vectors starts at 0.5 with tiny gradients (SGD stalls) while Adam saturates the
unnormalised vectors; one random negative per positive.

`cre-encoder.mjs` (CRE1): words and per-word 3-5-grams hashed separately into
16384 buckets, mean pooling, separate query/memory projection heads,
L2-normalised cosine, InfoNCE (tau 0.1) with same-subject hard negatives,
lazy Adam (bias corrections as products for a later Kotlin port).
Data: `relational-memory-data.mjs` (train/dev/sealed test, disjoint people and
values; test asks each fact once with a seen and once with a held-out question
form). Configuration chosen on DEV only; `relational-retrieval-audit.mjs`
measures TEST with 5 seeds, using the production word-evidence gate, neural
ranking, relative abstention and a literal match rule for untrained words.

Measured on sealed TEST (38 facts x 2 questions, 14 unanswerable):

| Method | seen-form correct | held-out-form correct | false answers |
|---|---|---|---|
| production lexical | 21/38 | 17/38 | 10/14 |
| CRE1 (seeds 1-5) | 24-31/38 | 12-15/38 | 2/14 every seed |
| SWB1 / SWB2 same pipeline | 11-18/38 | 8-16/38 | 7-9/14 |

Retention: after learning DEV pairs, TRAIN-domain retrieval stays 202-204/204.
Gate result: false answers fall from 10/14 to 2/14 on every seed, seen forms
improve, but held-out question forms are worse than lexical and seed 2 ties the
production total (38/76) -> **promotionEligible=false**. Not integrated into
the app. Next steps: improve held-out forms (e.g. combine lexical score into the
ranking, more varied training forms), Kotlin port with parity, then re-audit on
a new sealed suite.


## Development handoff integration audit — 2026-10-16

The following branch audit was performed against live PR #19, not merely PR descriptions:

| Source | Git state | Current integration |
|---|---|---|
| PR #14 | Draft/open on older base | ContinuumPersistenceFailureTest.kt byte-identical to the PR source; already incorporated |
| PR #16 | Draft/open on older base | Restored BenchmarkModel, BenchmarkRunner, BenchmarkReport and BenchmarkRunnerTest; added per-system cancellation and resource budget |
| PR #17 | Draft/open on older base | BoundedResearchTextSanitizer and AiContextCompiler identical to PR source; imported the missing adversarial edge-case tests |
| PR #18 | Draft/open on older base | Existing ComputeFabric omitted replay, node-binding and expiry checks; added an opt-in stateful verifier and regression tests |
| PR #21 | Draft/open repair demo | Not merged: fault-injection demonstration, not a verified production fix |
| PR #22 | Draft/open against main | Hourly schedule activation not merged to main; activation remains a separate owner decision |
| PR #23 | Merged | Contrastive-recovery code present |
| PR #24 | Open on older base | PR #24 head is an ancestor of the active PR #19 head; code already present |

Research-only CRE1 Kotlin inference can consume checksum-verified Node CRE1
checkpoints. Android CI generates a fixed trained Node reference, then verifies
Kotlin features, embeddings and retrieval scores, and checks Kotlin's scores
back against Node. This is **inference parity only**, not training parity.
CRE1 remains unpromoted. An unknown-name safety gate now compares whole
normalized vocabulary items, avoiding false known names from 14-bit collisions.

Offline learning is configured to run while charging, on a battery-not-low
and storage-not-low device. Runtime checks defer at low battery, elevated
thermal status, low free storage, or a 15-second cycle deadline. Its bounded
eight-entry phase log contains timestamps and status labels, not user data.

The benchmark harness is not yet connected to a production baseline adapter.
The strict result verifier is opt-in and does not enable outside compute nodes.
Actual CRE1 holdout improvements, Kotlin training parity, real-device tests,
automated code patch synthesis and authenticated GitHub-backlog linkage are
still open. No generative or autonomous system is declared production-ready.
