# Independent repair-learning evidence gate – 2026-10-26

Status: RED_REPRODUCED / FIX_IN_PROGRESS / REVIEW_ONLY.
Repository: Ullmann27/device-ai
Exact integration parent: Astra PR #159 commit `301fe804700189ee99533662ce57be96c29ae450`.
Working branch: `review/continuum-repair-learning-evidence-gate-20261026`.

## Reproduced defect
A source-level invocation of the exact upstream `.github/continuum/repair-ledger.mjs`, blob `4422e9d5e192c25b47a16f2c583e7468106bfa55`, invoked `recordSession(emptyLedger(), report)` with an unsuccessful non-scoped JS repair attempt: outcome `FAILED_TESTS`, `node-continuum` diagnostic `executed:0,failed:0,skipped:0`, no real test run. Upstream nonetheless returned strategy `experiences:[{kind:'JS_TEST_ERROR|JS_TYPO_REFERENCE',successes:0,failures:1}]`. This incorrectly turns an infrastructure outage into negative strategy-learning data. Astra already fixed a related but distinct issue in **reuse of prior scoped candidates**; the ledger *strategy-learning aggregation* remained permissive.

## Scope and gate
- Minimal source change only to selection of `FAILED_TESTS` for aggregate strategy experience: require at least one actually executed failed test in an allowed result record, with coherent counters and `passed:false`.
- Keep full diagnostic attempts and sessions in ledger regardless; do not erase failures.
- Preserve explicit `FAILED_SYNTAX` with `INVALID_JS_SYNTAX`, independently accepted successes and other existing learning rules.
- Write RED first, then GREEN. Run the real Node project tests when a full authorized checkout is available; no main commit, owner state, model weights or APK.
- This branch is based on a fixed Astra commit for reproducible review and must not overwrite Astra's PR #159 or claim production release.

## Evidence
Initial RED reproduced with the original module body in a fresh JavaScript V8 isolate using a test-only `mergeExperiences` shim and JSON clone; **not native Node full-suite**. Exact original SHA and source are named above. Test-and-production commits and runtime evidence will be appended.

## RED / GREEN source-identity results

A committed independent Node test was added FIRST in commit `0c0f88b21a0e564216c019f487482a6409f4cda3`, source blob `7f3b3fc658c31fa50f2f673f20e7a576b37299f0`. Against ORIGINAL production blob `4422e9d5e192c25b47a16f2c583e7468106bfa55`, its exact callback raises: `no evidenced failing tests means no negative strategy reinforcement expected [] got [{kind:'JS_UNRESOLVED|JS_TYPO_REFERENCE',successes:0,failures:1}]`. The negative fixture checks seven internally non-evidentiary cases: no tests, no executions, only skips, incoherent failed/executed totals, inconsistent passed flags, and invalid totals. It also retains real failed-test evidence and confirmed invalid JS syntax as legitimate negatives.

Implementation commit `5f71459f0b15237c6e1b63dd8ed4fece8d087849`, production blob `bc5160b10b869dde0d0ab16f0389cff9d7c5e294`: a `FAILED_TESTS` strategy observation can enter aggregate `experiences` only when at least one test result has `passed:false`, coherent integer `total/executed/failed/skipped`, and `executed>0, failed>0`. Infrastructure outages remain visible in `attempts` and `sessions`, not falsely used as training labels. This hardens the generic strategy learner separately from Astra's already implemented scoped retry-history filter.

The older `non-verified sessions retain their original failure diagnoses` unit fixture originally called an all-GREEN result `FAILED_TESTS`; corrected that contradictory fixture to one actually failed executed test in commit `8ef6c476a347fa900b3a055481b7b5801861d630`, test blob `70c1c7fd949b0ef8f58fb24980faf1e623db18f8`.

Isolated direct replay of the actual 11 registered test function bodies from the unchanged GitHub test blob:
- 10 tests executed **PASS**, 0 **FAIL**, 1 excluded because its dynamic import of `repair-strategies.mjs` requires a full Node module tree.
- Runtime: fresh V8 isolate, original production module function bodies and test bodies, with test-only `mergeExperiences`, `sha256`, `structuredClone` and assertion shims. THIS IS NOT native `node --test` and not a full project checkout or Android test.
- Native Node22 is installed on the independent local host, but the private full checkout and all Node module dependencies are not present in that environment. Do not mislabel isolated replay as native project validation.

## Safety / integration
- PR160 targets the moving Astra PR159 branch but is pinned to exact integration ancestor `301fe804700189ee99533662ce57be96c29ae450`.
- No change to actual tested repair candidate, patch critic, model weights, owner memory, sandbox, signatures, APK or `main`.
- Do not merge until actual `node --test .github/continuum/repair-ledger-independent.test.mjs` and the broader repair suite are run on a fully authorized checkout; reconcile any new Astra parent commits before review.
- Handoff point: take `.github/continuum/repair-ledger.mjs` and its regression tests from this Draft PR, verify the canonical scoped/non-scoped learning contracts, then run full Node and Android gates before any release.
