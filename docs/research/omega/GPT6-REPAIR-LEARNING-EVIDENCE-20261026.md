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
