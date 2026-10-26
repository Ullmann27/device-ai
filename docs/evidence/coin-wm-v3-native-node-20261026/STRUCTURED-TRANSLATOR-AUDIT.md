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
