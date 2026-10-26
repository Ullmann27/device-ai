# Argon COIN-WM / NTW1-v3 independent reconstruction – Node.js 22.16.0

Status: **CANDIDATE NOT ACCEPTED / RESEARCH ONLY / NO PRODUCTION INFERENCE / NO EXECUTION AUTHORITY**.
Owner pasted complete `continuum-coin-wm-v3-pr163.mjs` text in chat on 2026-10-26. It was NOT supplied as an immutable original file. This evidence applies to a manually transcribed, source-logic-matched reconstruction of the model, five training tasks, and five public compound task fixtures. It is **not byte-for-byte certification** of an upstream v3 source artifact.

## Native host test

Native runtime: Node.js `v22.16.0`, Linux. Author's predefined and reviewer-transcribed trusted small test functions were executed in a bounded research process for semantic comparison; the original unsafe v2 JavaScript evaluator was used **only for already-inspected fixture snippets** in this local reconstruction and is **not shipped** in the PR. No arbitrary agent/model output was executed.

The models each receive five development examples and a Pass@1 budget for each compound fixture. The 4-arm independent measured results:

| Method | v3 claim | Reviewer reconstruction |
|---|---|---|
| ZERO_MEMORY | 0/5 | **0/5** |
| EPISODIC_RETRIEVAL | ≤2/5 | **3/5** |
| HEURISTIC_OPERATOR_NO_NN | ≤2/5 | **2/5** |
| COIN_WM_NTW1_V3 | 5/5 | **3/5** |

The reconstructed model performed **1,210 weight updates**, with shared encoder L2 drift **4.863656376477088** after five learning phases. Thus shared encoder gradient updates occur; this alone does not prove retained generalization or necessity of EWC. The reported neural advantage over the equal-operator no-NN baseline is only **one of five** public tasks, with no untouched sealed holdout.

## Concrete operator generation failures

On B1 (sort-mutation bug in a compound function), the trained neural model correctly ranked the `DEV_A3_MUTATING_SORT` operator top (`score≈0.87164`), but its token-LCS patch application produced invalid syntax resembling:
```js
return items].sort((a,b)=>a-b);
```
The algorithm removes/inserts tokens with inadequate anchoring; it is **not an AST-aware JavaScript transformation**.

On B2 (division-by-zero bug with an unrelated `.slice` statement), the model correctly ranked `DEV_A4_DIV_BY_ZERO_AVERAGE` top (`score≈0.58967`), but its token edit application inserted an unconditional `return 0;` **before** the division, even for normal nonzero denominators:
```js
const dummy=[1,2,3].slice(0,3-1);
return 0;
return totalWeight/windowCount;
```
Thus **all five neural top operator identities are plausible, but only 3/5 patches actually pass**. The missing piece is safe condition-aware operator application / syntax and semantic validation, not just better NTW1 ranking.

These are direct counterexamples to the strong v3 `5/5` assertion. All cases are designer-visible fixtures, so even a fixed 5/5 on this benchmark would not constitute unseen generalization.

## High-severity Node vm boundary failure

A separate real Node22 test reproduced a constructor escape from the exact VM construction pattern shown in v3:
```js
const sandbox=Object.create(null);
Object.assign(sandbox,{Math,Array,Number,Object,JSON});
const context=vm.createContext(sandbox,{codeGeneration:{strings:false,wasm:false}});
vm.runInContext('Math.constructor.constructor("return process")().version',context);
```
Result: **`v22.16.0`** (the host process version), despite `typeof process` alone being `undefined` in the VM. The exported host built-in constructors defeat the purported security boundary. The `node:vm` module is **not a production security sandbox** even when strings/wasm codegen is disabled; do not execute untrusted code using it. The published v3 claim `constructor.constructor is blocked` is false for the proposed context.

## Additional scientific blockers

- `trainStep` actively updates `W_enc`, but `encoderWeightDriftL2()>0.25` proves movement, **not positive transfer**, **not retention**, **not EWC causal benefit**.
- `isTargetOp=(k===ep.opIndex)` remains the positive training target; evaluating a competing wrong-code candidate does not actually substitute the result as `verifiedSuccess`. The training labels can contradict actual patch outcomes.
- The "independent training validation" calls into the same unsafe `node:vm` evaluator and lacks a signed/verified canonical CCC1 test receipt.
- Mutation freezing does not solve VM escapes or give filesystem/network isolation.
- B4/B5 are essentially single-construct source functions despite the label "compound"; B1/B2 add decoy statements. A larger, sequestered compound benchmark is required.

## Safe integration policy

DO NOT import Argon's `runVmIsolatedSandboxVerification`, raw external constructs in `vm.createContext`, `new Function`, dynamic candidate code, or model weight promotion into `main`/CCC1. Preserve PR163 as RESEARCH_SHADOW. Use canonical already-reviewed isolated executor, immutable source/patch/test hashes, negative-run differentiation, explicit owner consent and independent tests.

Next steps: (1) implement side-effect-free v3 ranking-only shadow training from verified observations; (2) compare deterministic operator-only and neural ranking using externally obtained actual CCC1 outcomes; (3) reject bad token edits at parser/AST and real compiler gate; (4) prove benefit on sealed and adversarial compound tasks with nonzero baselines.
