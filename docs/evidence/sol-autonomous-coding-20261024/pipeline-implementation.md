# Scoped repair pipeline implementation — 2026-10-24

EXP-02/05/07/08/11/12. Source integrated, host infrastructure verified; no claim of Android/device verification. Root owns commits and final broader validation.

Changed `.github/continuum/repair-loop.mjs`, `backlog-repair-worker.mjs`, `autonomous-teamleader.mjs`, `repair-critic.mjs`, and additionally root-authorized `repair-ledger.mjs`. New `scoped-repair-pipeline.test.mjs` (13 tests).

- Owner worker now accepts executor, rejects untrusted executor before claims/checks, and threads it through baseline/candidate/independent checks.
- runProfile consumes trusted executor JUnit files and validates complete isolation evidence; no host JUnit fallback, no host crash rerun with executor. Existing host watchdog behavior retained.
- Executor owner cancellation fails closed by default with `ISOLATION_ABORT_WATCH_UNSUPPORTED`. Explicit `executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY'` enables bounded profile completion followed by checkpoint cancellation. Report/receipt records this limitation honestly; no claim of in-step executor cancellation.
- Frozen validated taskSpec binds owner task ID/source HEAD/fixed chain before claim and binds direct repairSession before baseline. Task grants max3 files via coding-task validator.
- Scoped proposals use `LOCAL_SCOPED_PATCH`; bounded revisions use actual latest check diagnostics plus <=3 history entries (candidate hash/outcome/diagnostic/hypothesis), reset to base each request, cap model calls 1..3 plus existing maxCandidates/deadline. Repeated patch hashes do not rerun tests. Provider failures count in localRequests. No verified-model-execution claim.
- Scoped proposals all traverse critic. Every host edit must satisfy full-file isLocalNumericPatch; otherwise trusted OS executor required before write/check. General source cannot silently execute on host.
- Scoped critic additionally forbids control files coding-*/isolated-* and direct runtime/test/report capabilities. This is defense in depth, not proof that arbitrary source cannot forge its own semantic output; all scoped successes remain owner-review evidence and never train/replay automatically via ledger.
- localTeamleaderDriver options forward scoped provider/executor/explicit cancellation mode to existing canonical owner worker. No second backlog/pipeline introduced. Legacy provider remains one call and single-file numeric boundary.

Tests:
1. New scoped tests first ran red: 7 expected missing-behavior failures/8 total. Additional critic+ledger tests first red (2 expected failures). Final `node --test .github/continuum/scoped-repair-pipeline.test.mjs`: 13 PASS, 0 fail, 0 skip, ~6.5s in parallel-loaded environment.
2. Final `node --test .github/continuum/repair-loop.test.mjs .github/continuum/backlog-repair-worker.test.mjs .github/continuum/local-repair-integration.test.mjs .github/continuum/local-numeric-patch.test.mjs .github/continuum/autonomous-teamleader.test.mjs .github/continuum/isolated-executor.test.mjs .github/continuum/repair-ledger.test.mjs`: 78 PASS, 0 fail, 0 skip, 7.245s. Includes real running-check owner abort/watchdog regression.
3. `git diff --check` clean.
4. Security agent independently reported actual branded Docker runProfile proof (3 real JUnit tests, isolation retained, host marker absent) in `/workspace/scratch/isolation-runprofile-proof.json`; owner broad two-file proof in progress with that agent. Root separately reports actual Qwen+Docker CCC1 correction/independent 12 tests proof; see root evidence for exact source binding.

Constraints/next step: full suite and real-model evidence indexing remain root-owned. No commits performed by this agent. No APK build, signing, installation or release. Revert changed modules/new test for rollback. OS-isolated report data are untrusted runtime artifacts, not independent attestation or semantic truth; owner review remains mandatory. Legacy Kotlin syntax infrastructure and newer abort/fresh-evidence logic retained.
