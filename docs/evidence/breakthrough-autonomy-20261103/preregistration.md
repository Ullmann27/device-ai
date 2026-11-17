# Preregistered executor coverage experiment

Frozen before source/test implementation, 2026-11-03. Base source:
`64ec06a73f50cae9ee49035c840710d87777d50f`.

Hypothesis: `compareRuns` in the existing CCC1 RED/GREEN verifier accepts a
source-only repair that makes the target pass while removing or renaming an
unrelated baseline test. Claimed counts can match both executions; this still
loses baseline coverage. Existing scalar receipt fixes cannot establish test
identity retention.

Scope: `.github/continuum/ccc1-redgreen-verifier.mjs` and its test file. No
worker, scheduler, HMAC/key, Android, memory or ledger framework changes.

Procedure: create temporary actual Git repositories with immutable test sources
and source-controlled conditional test registration. Run the existing fixed
Node/JUnit profile in fresh RED/GREEN detached worktrees for both commit and
patch receipt modes. Preserve baseline failure, exact fixture source SHAs,
patch hashes, commands, tool versions, verifier source hash and raw exit logs.
No model-provided shell or executable payload from a receipt is permitted.

Development cases: disappeared non-target test; same-count renamed test;
executed test becoming skipped/todo; valid repair retaining all tests;
coverage extension retaining all baseline tests; forged counts/self-report;
interrupted run rejection; duplicate/parameterized/ordered test identities.

Success criteria: at least one real executor-backed wrong acceptance is RED
before implementation, all coverage-reducing cases become REJECTED afterward,
valid and coverage-extending repairs remain VERIFIED, forged claims and
interrupted runs remain REJECTED. The complete CCC1 regression suite must pass.
Separate Quality owns new independent cases after candidate freeze.

Abort/reject: if the current verifier already rejects the proposed fault,
report that fact before choosing another fault. No tuning of Quality cases.
No claim that untrusted JUnit is cryptographic attestation or that host
synthetic deterministic repairs are general model coding intelligence.

Recovery: tests use disposable repositories only; compare caller checkout
HEAD/status and worktree lists before/after. No persistent replay or owner
permission semantics change. Re-running a receipt means fresh verification,
never reuse of worker-reported success. Existing interrupted-task approval
and SIGKILL recovery regressions stay mandatory.
