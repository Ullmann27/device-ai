# Statement-scoped patch translator & subprocess security audit — 2026-10-26

Status: **RESEARCH_SHADOW / PURE PATCH GENERATION ONLY / EXECUTION NOT AUTHORIZED**.
Branch: `research/continuum-coin-wm-independent-audit-20261026`; PR #163.

## Owner-provided idea and critical qualifications
Argon proposed (a) replacing partially matched token-LCS hunks with an atomic statement-scoped edit, and (b) using `spawnSync(process.execPath, ['--disallow-code-generation-from-strings', '-e', ...], {env:{},timeout:400})` as an OS sandbox.

The atomic-edit concept solves the known B1/B2 syntactic damage when a unique statement matches. The subprocess isolation claim is **false**: an independently executed trusted Node.js 22.16.0 child, launched with exactly the proposed empty environment and code-generation flag, returned
```json
{"fsAccess":true,"processVisible":"object","networkModule":"function","workingDirectory":"/tmp"}
```
using only non-destructive checks `require('node:fs').existsSync('/etc/passwd')`, `typeof process`, `typeof require('node:net').connect` and `process.cwd()`. Thus the spawned process still has filesystem access and network primitives. A child process and a timeout limit a process's lifetime, **not** its filesystem/network permissions, OS user privileges or kernel attack surface. Modifying `process.version`, `process.env`, `process.binding` or `process.dlopen` is neither mandatory nor an OS security boundary. **DO NOT ship Argon's subprocess verifier for untrusted code.** Keep existing `.github/continuum/isolated-executor.mjs` as sole authorization-capable executor; on missing OS isolation fail closed.

## Actual implemented source

- `.github/continuum/structured-statement-translator.mjs`, current Git blob `9c9e2cc7abaffd6b26c70aab34e695e9da75f22a`.
- `.github/continuum/structured-statement-translator.test.mjs`, current Git blob `04d2a1ec9095da3c1be2767f938837d9b14b9aa9`.

Both are confined to the **research-only** PR #163. The translator:
- Accepts at most 16,384 characters, 1,024 tokens, 80 top-level statements and 12 parameters, on a restricted single ordinary JS function-declaration subset.
- Uses strict lexical completeness with unsupported comment/string/template/regex/nested-brace syntaxes rejected instead of guessed. It is **not a complete JS AST parser** and does not claim to be.
- Induces either one fully preserved `STATEMENT_ATOMIC_REWRITE` or one complete single `STATEMENT_PREPEND_GUARD` from a before/after *example*, after syntactic parsing.
- Alpha-maps parameters and local variables only where unambiguous. A collision between a formal argument and a similarly spelled member-property token is rejected. Uses a **unique exact normalized statement key** to locate the target.
- Rejects no-match, multiple-match, unsupported source and syntax-invalid candidates **without outputting a half-patch**. A generated patch's status is always `CANDIDATE_UNVERIFIED`, never `VERIFIED`.
- Uses `new vm.Script` for **syntax compilation only**, not `runInContext`, `runInNewContext` or code execution. There is no `spawnSync`, `Function`, file I/O, CC1 grant, tool dispatch or model weight write in this module.
- Does not alter or access `main`, owner state, system APIs, weights, APK signing or installed Android applications.

## Independently executed tests

1. Local trusted Node.js 22.16.0 test harness for the precursor restricted translator: **6/6 passed**. This is an independent **prototype**, not a native run of the exact current GitHub blobs.
2. **Exact committed GitHub** production and test callback bodies reloaded into isolated V8, with a syntax-only `vm.Script` compilation shim and no actual candidate code execution: **5/5 tests PASS**.
   - B1 immutable `sort` target with preceding `slice`/local noise: a complete `return [...items].sort(...)` patch is emitted, never `return items].sort(...)`.
   - B2 divide-by-zero target with preceding `slice` noise: full `if (windowCount === 0) return 0;` precedes the pre-existing nonzero calculation; no unconditional `return 0`.
   - B3 nonnegative `Math.min` clamp with unrelated `slice`: one atomically rewritten statement.
   - B4 one-based slice offset: a unique local assignment is atomically rewritten.
   - B5 exclusive slice-end correction with legal parameter `length`: supported, with member-property collision rejection.
   - Unsupported, conflicting or no-match code safely rejected, and test of source containing a would-be side effect confirms no code is executed during translation.

**Five syntactically correct candidate source transformations are NOT five independently verified programming fixes.** None of the generated new candidates was executed with the production isolated executor or accepted as an authenticated CCC1 repair. Their public, explicitly defined source examples are also NOT a sealed holdout.

## Mandatory next gate

1. Re-run `node --test .github/continuum/structured-statement-translator.test.mjs` with the EXACT GitHub blob under Node 22 on an authorized full checkout.
2. Define an approved, restricted, immutable **research candidate descriptor** for feeding to the existing canonical CCC1 patch-review path. No direct invocation of isolated executor by model output.
3. Verify candidate source SHA, permitted patch scope, original test failure, compiler parse and real isolation capability. Only external independent test results may attach the label `VERIFIED`.
4. Run a sealed multi-family benchmark with a matched operator-only control and an untrained/ablated neural ranker. Confirm changes improve Pass@1 on unknown tasks, not just published examples.
5. Maintain PR163 as Draft; no merges or APK installation before full Android and device gates.


## 2026-10-26: neural-to-atomic research wiring added

Follow-up commits:
- `.github/continuum/structured-statement-translator.mjs`, current blob `97e05a743ab1822c5952f1e2f13ff8e560639a77`: legitimate parameter `length` supported only if it cannot collide with member-property `.length`; a private module-local `WeakSet` brands frozen induced operators, preventing a caller from forging an executable `applyToCode` callback merely by copying the expected shape.
- `.github/continuum/coin-wm-v3-structured-shadow.mjs`, blob `817d3713b0e82d21d2890b47d5031bde54f464b2`: **connects the existing NTW1-v3 research-only ranking to actual atomic statement operator application**; bounded maxCandidates 1–2; returns only `RESEARCH_SHADOW`, `CANDIDATE_UNVERIFIED`, `promotionEligible:false`, and `needsIndependentExecution:true`. It never calls `isolated-executor.mjs`, spawns a process, accesses owner memory, writes patches, runs generated code or marks a test verified.
- `.github/continuum/coin-wm-v3-structured-shadow.test.mjs`, blob `49d400841edf13ad54885ad5953dfca35cc05ff8`: **3/3 exact GitHub test callbacks PASS in V8**. Includes explicit rejection of a forged operator with a potentially side-effecting callback (the forged function is never invoked), strict input/attempt constraints, and an NTW1-ranked valid structured candidate in the bounded research-only adapter.
- The existing `.github/continuum/structured-statement-translator.test.mjs`, blob `04d2a1ec9095da3c1be2767f938837d9b14b9aa9`: **5/5 exact GitHub test callbacks PASS**. B1–B5 known candidate transformations and negative cases are covered. These are publicly specified fixtures, not sealed holdout evidence.

**Test environment qualification:** The original GitHub module bodies and test callback functions were re-fetched after commits and evaluated in a fresh V8 isolate with a test-only compile-only `vm.Script` shim. They were *not* executed through `node --test` in an authenticated complete Git checkout. A similar precursor translator (not identical blob) ran 6/6 actual `node --test` locally under Node22. Do not aggregate those counts into a fabricated native suite result.

**Review gate now clearer:** NTW1's research score can rank a **hypothesis**, and statement-level translation can create a **candidate**. Neither action counts as a successful repair nor guarantees semantic correctness. CCC1/ToolGate/isolated-executor remains the ONLY authorized pathway to try source code, and only an independent verified test receipt can move a candidate to a reviewable result. A declared SHA string is not sufficient as such a receipt.


## Provenance-bound shadow proposals (further hardening)

The ranker-to-translator adapter now requires two explicit immutable task identifiers before returning **any** candidate:
- `sourceCommit`: a syntactically valid lowercase 40-hex Git commit, preserving the already-reviewed source identity expected by the canonical Android↔Termux coding task.
- `taskSha256`: a lowercase 64-hex typed task fingerprint.
- `sourceSha256`: SHA-256 digest of the source snippet calculated by Node `crypto.createHash('sha256')`.
- `candidateSha256`: SHA-256 digest of the proposed edited source, also calculated by Node `crypto`.

These are **bindings and consistency checks**, not authorization, attestation or a proof that the commit/task fingerprint was verified by Git. A future CCC1 adapter must compare them to a canonical owner-approved grant, source tree and independently measured receipt before execution or learning.

Exact current GitHub blobs:
- `.github/continuum/coin-wm-v3-structured-shadow.mjs`: `22bcd1a7b105fa4e3f4e4d8acb1f881ed243f08a`.
- `.github/continuum/coin-wm-v3-structured-shadow.test.mjs`: `7068031daef4f86fb71f5ca40cd06697ac9a3a35`.
- `.github/continuum/structured-statement-translator.mjs`: `97e05a743ab1822c5952f1e2f13ff8e560639a77`.

Exact-source V8 replay after the final identity binding: **4/4 adapter tests PASS** and the separately re-fetched **5/5 translator tests PASS**. Because the V8 isolated runner has no native Node `crypto`, its hash adapter is explicitly **test-only deterministic, NOT cryptographic**; it verifies data flow and digest consistency, not real SHA-256 generation. The source uses the standard actual Node crypto API; full Node CLI native suite on current GitHub blobs is still NOT_RUN.

The adapter rejects missing/malformed source commit or task hash, bad/mismatched shadow models, invalid operator sets, invalid states, over-budget candidate counts, and a forged `applyToCode` implementation. It does **not** execute code, call CCC1, promote a model or produce an independent verification receipt.
