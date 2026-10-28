# Continuum Fusion: full-regression and device-quality gate (2026-10-28)

This work is an isolated continuation of [Draft PR #203](https://github.com/Ullmann27/device-ai/pull/203), originating at its verified head `b12dfab392022d050c072419f8ed2fdf2c71bfae`. Do not merge or alter main, #198, #197, #196 or the source branch of #203. EXP-04/05/06/07/11, canonical experiment contract and Issue #41 remain authoritative.

**Entry status: PLANNED / NOT_RUN.** Earlier #203 records prove 56/56 targeted real Kotlin-JUnit PASS, 16/16 targeted Node tests PASS, compilation and configured NewApi lint PASS on Linux JDK17 / Gradle 8.11.1 / SDK36; this is **not** the full JUnit suite. This runtime has no access to `/workspace/device-ai-fusion` or the verified JDK17/Gradle8.11.1/SDK36 local toolchain, and cannot access the owner's Fold7 or proprietary Gemma weights. It cannot honestly claim full-suite, real inference or phone verification.

The next deliverable is a fail-closed, local-only full-suite runner that records the actual commit and command, executes **unfiltered** `:app:testDebugUnitTest` with the existing Gradle and SDK setup, and writes original logs and machine-readable JUnit totals outside the source checkout. It must not sign or install an APK, access owner memory, require GitHub Actions, or make external AI calls. A PASS is permitted only if the Gradle task actually exits 0 and all XML suites have zero failures, errors and skips. Preserve the complete original error logs if it fails.

After authentic full-regression PASS, a separate owner-authorized Fold7/Gemma/real-world search quality test may follow; absent actual device and model access, those gates remain NOT_RUN.

Rollback: keep this new Draft PR unmerged; it is stacked directly on #203, with no changes to the parent's source branch.
## Implemented and locally verified

The following three executable files are now committed:

- `scripts/continuum-fusion-full-regression.sh`: exact-SHA, clean-tree, explicit JDK17/SDK36/Gradle8.11.1/AAPT2/Node-LTS gates; **unfiltered** and uncached `:app:testDebugUnitTest` plus `:app:lintDebug`; existing Node research contracts; no cloud/CI provider or signing.
- `scripts/continuum-fusion-regression-report.mjs`: checks complete/fresh Gradle JUnit XML, nonzero exit codes, failure/error/skip totals, and exports the original XML and logs to an external, private evidence directory with a SHA-256 manifest.
- `scripts/continuum-fusion-regression-report.test.mjs`: six independent Node regression tests for success/failure/staleness/missing reports and evidence collection.

Actual local validation: **6/6 Node tests PASS**; `bash -n` PASS; scripted preflight blocked with **exit 69** because the authentic source checkout is absent. The three locally tested files match their corresponding remote GitHub blobs byte-for-byte: `d0f92a066d24e751728bffeaf3eff6392a9e9aa0`, `433bcda4610bfe186fd8a1559193aa5bb42a7663` and `4adf006deab97c24cfd4f87cc7d11e81e09b1046`. [Raw reproducibility statement](../../evidence/continuum-fusion-regression-gate-20261028/local-verifier-validation.md).

**Actual full JUnit suite = NOT_RUN / UNAVAILABLE** here (the previous cloud environment cannot be reached from this separate session). The prior 56 targeted Kotlin/JUnit tests remain historical evidence from PR #203 and are not full regression evidence for this child PR. New six Node tests validate the reporter, not Android or Kotlin behavior.

**Next operation on the already authenticated owner Cloud/Termux checkout**: switch to this Draft PR branch, use the exact existing toolchain described above, and execute `bash scripts/continuum-fusion-full-regression.sh "$(git rev-parse HEAD)"` from a clean checkout. Keep the resulting private evidence outside the checkout until reviewed. A PASS requires `summary.json.status = "PASS"` at the tested HEAD and its accompanying logs, full XML and hashes.

**After actual full-suite PASS**, plan a separate real device/Gemma quality gate: same installed signed main-app build, same model SHA, measured repeated prompts, fixed source-verified research tasks, permission-denial and missing-evidence cases, latency/peak-RAM/thermal observations, baseline/candidate comparison. Do not call it an improvement unless data support it. Device, original-P12 signing, live Gemma model, Fold7 and autonomous web-research benchmarks remain `NOT_RUN` and cannot be executed by the present GitHub-only connection.

Rollback: leave Draft PR #204 unmerged or revert the isolated QA-script commits; no changes to #203's production source, no owner fact reads, no repo branch deletion or forced updates.
