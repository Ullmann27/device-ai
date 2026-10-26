# COIN-WM / NTW1: independent candidate audit (2026-10-26)

Status: **RESEARCH_SHADOW / CLAIMED BREAKTHROUGH FALSIFIED IN LIMITED HOST REPLAY / DO NOT PROMOTE**.
Exact source URL supplied by the owner: https://share.gemini.google/jmJTIeXeuGbN
Published Gemini public text: https://gemini.google.com/share/1d343cfa0bf5
Source: 35,210-character JavaScript prototype included in Argon's shared response.
Integration parent: PR #162, immutable Git SHA `cafc337a14eca01dc49e38eea3dd299034460552`.
Experiment IDs: EXP-02, EXP-03, EXP-04, EXP-05, EXP-06, EXP-07, EXP-12.
No owner data or model weights read/modified; no original Gemini code imported into production or executed on Android.

## Exact independent host replay

The 864-line reference prototype was extracted from the source's second fenced code block. Its original function bodies were evaluated in a fresh, restricted V8 isolate; Node imports/exports were replaced with test-only shims for `node:test`, `node:assert/strict`, `crypto.createHash` and monotonic clock. The predefined samples were the only dynamic code evaluated by the prototype. **NOT** a native `node --test` runner, Android Sandbox or independent holdout. The pseudo-crypto shim is not real SHA-256, but this prototype's benchmark/ranker does not use cryptographic integrity as a selection signal.

Three original test callbacks:
- `EXP-1: 3-Agent Benchmark...`: **FAIL**, first assertion already fails because `ZERO_MEMORY` solves **1/5** but test demands 0/5.
- `EXP-2: Infrastructure aborts...`: **PASS**.
- `EXP-3: Continual Learning Retention...`: **FAIL**, first task's transfer B1 is not retained after later training.

A separate direct rerun of `COIN_WM` after the same five development experiences and at one candidate attempt per held-out task yields **1/5**, not the promised 5/5. The successfully chosen operator is only the final thermal-clamping task. Matrix checking each of the five prewritten operators against each of the five public 'holdout' examples shows exactly one successful operator per task, but NTW1 ranks the *same last operator* first on all five tasks.

| Held-out public fixture | Correct handcoded operator exists | NTW1 top-ranked operator | Top-1 solved |
|---|---|---|---|
| B1 PCM window | ONE_BASED_WINDOW | LOWER_ZERO_CLAMP | no |
| B2 telemetry burst | HALF_OPEN_END | LOWER_ZERO_CLAMP | no |
| B3 immutable latencies | IMMUTABLE_SORT | LOWER_ZERO_CLAMP | no |
| B4 battery division | SAFE_DENOMINATOR | LOWER_ZERO_CLAMP | no |
| B5 thermal budget | LOWER_ZERO_CLAMP | LOWER_ZERO_CLAMP | yes |

This is a sample of five *public, hand-picked* examples, NOT a scientific generalization demonstration.

## Critical architectural issues

1. **Operator library is five explicit handcoded text/regex rewrite patterns**, not generic AST anti-unification from experience. The 'induction' function checks handwritten pattern branches against the development fixed code and returns hardcoded transforms.
2. **Weak/biased baseline:** ZERO_MEMORY uses two fixed, mostly irrelevant guessing rules, while RETRIEVAL copies the complete unrelated function body; neither receives equally capable program synthesis or an operator-only ablation. A fair comparison must include *same operator set with deterministic precondition ranking*, random policy, learned policy with frozen/zero weights, and the canonical existing CCC1 local coding model at matched budgets.
3. **No real safe sandbox**: `runIndependentSandboxCheck` uses `new Function` to run candidate code in-process. This would permit arbitrary code execution/hang and is disallowed for live untrusted candidates. Use the existing approved isolated executor and ToolGate, never import this test evaluator.
4. **Undemonstrated causal learning:** Training uses provided `verifiedFix` gold code for all five hand-authored development examples; the single-pair operator hypotheses are not verified by randomized counterfactual controls. The observed ranker tends to collapse to the final operator.
5. **Insufficient retention evidence:** One previously seen transfer case checked twice cannot measure EWC's backward transfer, and the supplied retention assertion actually fails.
6. **Implementation/document mismatch:** The discussion claims ~4,800 and elsewhere 1,705 parameters. The actual frozen constants `STATE_DIM=24, OP_EMBED_DIM=8, HIDDEN_DIM=24, TRANSITION_DIM=8`, eight operator embedding slots, and `parameterCount()` imply **1,081 trainable scalar parameters** (64+768+24+24+1+192+8), excluding Fisher/anchor storage. No independent checkpoint persistence implementation is included in the shared JS prototype.
7. **Leakage / no untouched holdout:** Proposed public tasks, operator families and pass assertions are all in the same source file. Test cases are visible to the author; cannot count as frozen secret prospective generalization.
8. **Security promotion risk:** The proposed Android bridge contracts are conceptual only; do not invent existing API compatibility or let experimental operator rankings grant shell/tool/model/owner fact authority.

## What is valuable

The research hypothesis itself is worth pursuing as **safe SHADOW ranker only**: delexicalize verified repair diagnostics, abstract structural preconditions, keep observed independent test/evidence as operator outcomes, and compare a learned ranker against deterministic, fair zero-memory, retrieval and operator-only baselines on genuinely novel task families. Keep neural weights frozen for production; learning must remain versioned/rollbackable and prove positive transfer.

## Next gates

1. Introduce a small, side-effect-free **scorecard** for externally verified operator outcomes, capturing baseline/learning/control and ablation metrics; NEVER eval or execute arbitrary candidate code in the scorecard.
2. Mine **only** provenance-bound real CCC1 repairs with baseline failure, candidate/independent success and cryptographically verified source/patch identities. Filter synthetic/infra/cancellation/generator syntax errors into separate categories; no mislabeling.
3. Pre-register new varied task families, code mutations, adversarial negative-transfer tasks and unseen code idioms. Seal withheld cases away from model/prompt/engineering.
4. Run equal-budget baselines, random/operator-only/NTW1 ablations with at least several independently sourced task families; report confidence intervals and failures. Explicitly separate lexical transfer, structural transfer, neural contribution and total agent success.
5. No on-device model promotion until Node, Gradle, native Termux/Fold7 and sandbox gates pass.

This audit branch contains no unsafe Argon original code or novel model activation. Keep it Draft and review-only.
