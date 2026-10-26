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

## 2026-10-26 follow-up: Argon's v2 response (new input, not the previous shared v1 link)

The owner subsequently pasted a second, fully specified `continuum-coin-wm-v2.mjs` response into chat, which fixes the old asymmetrical contrastive sampling by all-pairs replay and separate per-operator policy/transition heads, and replaces the five hand-written patch rules with generic, alpha-normalized token-LCS edit scripts. **The v2 source is not published as an immutable original file**: the reviewer manually reconstructed its named function bodies and the ten stated fixture bodies into an independent Node22 local harness. All results below are **faithful-reconstruction measurements**, not original-file byte-identity certification. The reconstruction was isolated from the production GitHub repository and contained only the deliberately selected small benchmark functions.

### Real native Node22 outcome

- **EXP-1:** FAIL against v2's exact asserted triple `ZERO_MEMORY=0, EPISODIC_RETRIEVAL=0, COIN_WM=5`. Actual reconstructed benchmark counts: **0 / 2 / 5** respectively. That is an actual improvement over v1's observed 1/5 neural solution rate, but the baseline was not zero.
- **EXP-2:** PASS. The explicitly marked infrastructure abort caused no neural update and no operator induction.
- **EXP-3:** PASS. After five sequential learning phases all five current example tasks could be solved at Pass@1.
- Neural policy's learned top-1 operator is the correct one in all five supplied v2 tasks; model update counter 990.
- A simple **first applicable changed-code** ranker (without neural training) solves **3/5** on the same public fixtures, demonstrating that the operator edit mechanism alone already contributes transferable power; this is an informative informal ablation, *not* a tuned or statistically controlled research comparison.
- `W_enc` remains byte-for-byte equal to its identity-like initialization after all 990 updates: the supplied `trainStep()` updates *only* per-operator policy and transition heads. Hence the shared state encoder is frozen, not learned as described, and the observed retention does not prove that EWC is causally required.

### Additional independent negative controls (native Node22)

Five specific tests reproduced:
1. `runBoundedSandboxVerification` gives candidate code unrestricted `process` object access because it uses an in-process `new Function`. Direct function `return typeof process` passed with the expected `'object'`.
2. `forbidInputMutation` only snapshots `clonedArgs[0]`; mutation to a *second* input parameter passes unobserved.
3. The token regex drops quotes from JS string literals, so it is not a general JavaScript parser/AST normalizer.
4. `ingestVerifiedDevelopmentExperience` trusts its `verifiedFixedCode` parameter and trains a *positive* policy label even for a deliberately incorrect purported fix: no independent execution of this claimed gold code before operator induction. Within the all-pairs loop `verifiedSuccess=(k===ep.opIndex)` is a match label, not the result of actual independent test execution on the selected operator.
5. The shared encoder `W_enc` stays fixed after all learning steps.

A short-lived child Node22 process attempted a supplied **valid but nonterminating** `function count(){for(;;);}` fixture: its loop is not recognized by the simple regex instrumentation; the separate external process was stopped by the reviewer's **700ms hard timeout**. This is a directly observed escape from the purported in-process `__gas` limit. No such code was run inside the application or in a long-lived agent process.

### Implications

- **Conditional scientific credit:** Argon's v2 prototype solves 5/5 *known public, designer-visible* samples in the independent native Node22 reconstruction, improves training order bias, and yields a plausible trainable per-operator ranking policy. It has not shown a new sealed holdout, robust AST transfer, statistically meaningful cross-family generalization, learnable shared world representation, or production-safe candidate execution.
- **Absolutely do not import** `runBoundedSandboxVerification`, `new Function` evaluator, pseudo-gas injector, unsound trust in `verifiedFixedCode`, or v2 weight updates as core production authority. Use the existing reviewed CCC1 isolated executor and real signed test receipts; without a valid source/patch/test evidence chain no experience should train the neural policy.
- **Operator taxonomy:** Token-LCS can induce candidate source-to-source edit scripts, but is not a real JavaScript parser and does not establish semantic causality. Repeated same-source occurrences, string/regex literals, scoping and alpha-renaming require negative controls and AST-level validation before reuse.
- **v2 retest conclusion:** A promising research step with confirmed limitations, **not** a completed on-device continual-learning breakthrough, no new APK, no main merge.

### New safe code integrated for the next phase

Committed a standalone, side-effect-free research module:
- `.github/continuum/coin-wm-shadow-scorecard.mjs` blob `8ed6c3f68f6a0c61151d0e394cf43410dc6d43fb`
- `.github/continuum/coin-wm-shadow-scorecard.test.mjs` blob `11764ad9b2b665afab7515065ffcaaec5ff61a8b`.

This module summarizes **four matched-budget arms** `ZERO_MEMORY`, `EPISODIC_RETRIEVAL`, `OPERATOR_ONLY`, `COIN_WM` with syntax checks for source/task/evidence SHA identifiers, nonzero executed tests, consistent pass/fail flags and duplicate cases. It contains **no patch execution, model training, model weights, access to owner state, or model promotion capability**. Its output always explicitly declares `status=RESEARCH_SHADOW`, `promotionEligible=false`, `needsIndependentEvidenceVerification=true`; a hash-shaped string is NOT attestation that the corresponding test actually ran. Its exact committed GitHub test callback bodies were executed directly in a fresh V8 isolate, **4/4 PASS**, after re-fetching production and test files by immutable Git blob. Full native Node for these exact GitHub files still needs to run in a synced checkout. This is the correct first low-risk integration step to prepare independent CCC1 evidence comparisons before any neural activation.

Next research: audit actual `repair-ledger` schema in the checkout; add a *read-only* verified-CCC1-evidence adapter with explicit allowlists, immutable check-run identities and a sequestered holdout benchmark. Distinguish genuine test failures from compiler/generator/sandbox/infrastructure errors. Require robust operator-only controls and protection against proxy model outcomes that aren't real independent sandbox evaluations.


## v3 owner-provided update: independent assessment and bounded implementation (2026-10-26)

The owner pasted a complete third iteration of the research prototype, arguing that `node:vm`, generic LCS token patch scripts, a jointly trained encoder and a four-arm compound fixture would prove real transfer. **Independent Node.js 22.16.0 host verification rejected two central claims:**

1. The exact proposed VM creation pattern passes host `Math`, `Array`, `Number`, `Object`, `JSON` into `vm.createContext(Object.create(null), {codeGeneration:{strings:false,wasm:false}})`. Inside, `typeof process` is undefined, but `Math.constructor.constructor("return process")().version` actually returns the host `v22.16.0`. No arbitrary external code was executed by Continuum; this was an isolated local security counterexample. Native `node:vm` is **not a security boundary**; the project's original OS-isolated executor remains mandatory.

2. A detailed local manually faithful, **not byte-for-byte original**, reconstruction of the v3 benchmark (same stated four arms, five dev fixtures and five compound task fixtures; actual Node22; inspected small trusted scripts only) produced **0/5 ZERO_MEMORY, 3/5 EPISODIC_RETRIEVAL, 2/5 HEURISTIC_OPERATOR_NO_NN, 3/5 COIN_WM_NTW1_V3**, versus the author's asserted `0/≤2/≤2/5`. The v3 shared encoder *does* update: **1,210 learned steps** and **4.863656376477088 L2 weight drift** after five training phases. Thus the previous **frozen-encoder bug is addressed**, but 3/5 does not prove robust transfer or EWC benefit.

3. Important diagnosis: NTW1 ranks the intended hand-built operator first on all five public fixtures. The code edit **application** fails for B1 and B2: a malformed token LCS edit gives `return items].sort(...)` for the sorted-array mutation, and a misplaced unconditional `return 0; return totalWeight/windowCount;` for zero-division. This is **repair-synthesis / edit-context failure**, not exclusively neural ranking failure. LCS token hunks are not JS AST patches. Any meaningful improved experiment requires a real syntax/AST and independent output validation gate.

4. v3 still trains with `isTargetOp=(k===ep.opIndex)` as the positive label, even though candidate `wrongCheck` is measured. A supposed exact operator match is not proof that the generated patch passed independent tests. The demo's `verifiedFixedCode` check is in the unsafe VM evaluator, not a canonical receipt. A secure read-only external evidence adapter is the next genuine requirement.

5. The fixed predefined public test cases are not an independent sealed holdout. B4/B5 are effectively simple functions despite compound labels. A 3/5 vs 2/5 result on five designer-visible tasks has insufficient sample size to establish significance.

### Concrete research-only code committed now

- `.github/continuum/coin-wm-v3-shadow-model.mjs`, pinned Git blob `bd6a3c388da303f06a77ee98c37c6804636e0a65`: strict 16-dimensional state input, 12-unit tanh shared encoder with analytic gradient, 8-dimensional transition predictions, per-operator policy heads, bounded update rates/budgets, EWC anchor bookkeeping, deterministic initialization, score/rank only. **No VM, Function/eval, files, network, owner memory, tools, weights promotion, or direct CCC1 executor.** Training entry is explicitly named `trainResearchSample`, not verified production learning. All results remain `RESEARCH_SHADOW`.
- `.github/continuum/coin-wm-v3-shadow-model.test.mjs`, blob `14775f7f29d860131519aae8a71ceab63567b0f2`: 4 direct test callbacks in fresh V8 against exact GitHub-fetched original source bodies, **4/4 PASS**: shared encoder updates, finite state/input and learning-rate rejection, reproducible deterministic scores, no execution/promotion API.
- The earlier safe `coin-wm-shadow-scorecard.mjs` remains unchanged. Neither module is wired to the Android production app. It is a research instrument waiting for real signed external CCC1 failure/success observations.

Complete v3 runtime measurements and examples: `docs/evidence/coin-wm-v3-native-node-20261026/INDEPENDENT-RECONSTRUCTION.md`.

**Next gate:** native `node --test` for both exact GitHub source/test blobs in an authorized full checkout, proper safe owner-approved isolated execution, reliable structured patch/AST translation, source-identical repaired fixtures, and a new sequestered adversarial benchmark with an explicit no-neural operator-only control. No automatic merge to `main`.
