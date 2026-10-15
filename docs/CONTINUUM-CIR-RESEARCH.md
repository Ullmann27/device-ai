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
