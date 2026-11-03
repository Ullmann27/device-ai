# DELTA independent first assessment

Candidate `799dd763e5a20de29d78fdd1dd26f7a5a5f0b0b0` improves coverage retention,
but does not yet meet the entire frozen receipt-consistency contract. No production
changes were made by this challenger. Assessment completed 2026-11-03 UTC, within
the bounded first-assessment window.

The case manifest was committed and pushed at
[`e12c9dc8`](https://github.com/Ullmann27/device-ai/commit/e12c9dc8) before the
candidate SHA was supplied or source inspected. Draft PR:
https://github.com/Ullmann27/device-ai/pull/407, based on
`codex/team-chief/omega-20261103`.

| Scope | Baseline | Candidate |
|---|---:|---:|
| Actual fixed-profile / receipt observations meeting frozen oracle | 13 / 42 | 25 / 42 |
| Parser-only diagnostic observations meeting frozen oracle | 2 / 4 | 2 / 4 |
| Combined harness observations | 15 / 46 | 27 / 46 |
| Caller HEAD/status/worktree-list preservation during receipt calls | 41 / 41 | 41 / 41 |

These are challenge observations, not the repository's complete unit suite.
Each receipt call invokes the actual verifier's default `runNodeProfile`; no
replacement or mocked runner is passed. Separate setup runs obtain truthful
receipt counts. Tests execute in the verifier's temporary detached worktrees.
The harness exits 1 for both arms because the frozen assertions have findings.

The candidate passes all frozen coverage-loss challenges: source-driven deletion,
equal-count replacement, SKIP/TODO conversion, duplicate occurrence loss, nested
suite loss, same-stem path collision, and ambiguous alias lookup. It also rejects
three malformed claim arrays without the baseline's uncaught exceptions. All
eight legitimate receipt checks pass, including source-driven test extension,
retained nested cases, a unique alias, and unchanged pre-existing SKIP/TODO.
Timeout followed by a fresh successful run passes in both arms.

There are 17 remaining real receipt-consistency findings. All also exist in the
baseline; they are not regressions introduced by the candidate. The underlying
ordinary fixture genuinely executes RED 3 tests / 1 failure / exit 1 and GREEN
3 tests / 0 failures / exit 0. Its repair is real. The findings below concern
acceptance of inconsistent receipt fields or target lists, not fabrication of
those actual execution results.

1. **D13: two target strings resolve to one test.** Set `targetTests` and
   `green.passedTargets` to `["check.target", "test.target"]`. Both names resolve
   uniquely but to the same canonical testcase. Candidate returns `VERIFIED`.
   Candidate lines 108 and 133–135 check string uniqueness and each lookup, but
   not uniqueness of resolved target identities. This violates the frozen target
   identity contract; it does not prove an unrepaired test was accepted.
2. **D16: ignored contradictory run metadata, 11 observations.** For the genuine
   ordinary receipt, change exactly one field: red `exitCode=0`; green
   `exitCode=9`; either side `command="not-node-redgreen"`, `timedOut=true`,
   `cancelled=true`, or `skipped=99`; or red `passedTargets=["not.real"]`.
   Every such receipt returns `VERIFIED`. Lines 141–144 compare only selected
   claims. The attestation still reports the real observed exit codes and counts;
   these findings must not be represented as arbitrary worker authentication or
   runner-selection attacks.
3. **D17: patch-mode envelope/authority inconsistencies, 5 observations.** On the
   genuine patch receipt, individually set `approvedForMerge=true`,
   `installed=true`, `deviceVerified=true`, `schema="forged"`, or `version=999`.
   Each returns `VERIFIED`. Commit mode explicitly checks these properties at
   lines 152/157; patch mode at lines 172–188 does not. The attestation itself
   grants no merge/install/device authority, but the same receipt is accepted
   despite contradicting the existing commit-mode envelope contract.

**Parser-only diagnostics D18:** truncated or unclosed XML containing a complete
testcase yields one usable parsed case. Empty and zero-case XML yield zero cases.
No fake report or injected runner is used to claim an end-to-end bypass. The fixed
Node reporter normally owns its output; forcing report absence/malformation
through host tampering is outside this task's OS-sandbox boundary. Actual report
absence is therefore NOT_EXERCISED. Parser hardening may be useful but these two
diagnostics are not among the 17 actual receipt findings.

Reproduce from this branch with Node v24.19.0 and Git:

```sh
node tools/delta-challenger/challenge.mjs /workspace/delta-challenger/.github/continuum/ccc1-redgreen-verifier.mjs /tmp/delta-baseline.json
node tools/delta-challenger/challenge.mjs /workspace/delta-challenger-candidate/.github/continuum/ccc1-redgreen-verifier.mjs /tmp/delta-candidate.json
```

For a minimal reproduction, use the harness's `ordinary` fixture (three tests,
one source-file arithmetic-value repair) and apply any single mutation listed
above to its otherwise truthful receipt. The full mutated receipt and returned
attestation for each observation are preserved in `candidate-results.json`.
Fixture source/test hashes and commits are also included, allowing exact replay
without retaining temporary worktrees. No network call is used by the harness.

Exact source/evidence identities:

- Baseline source commit: `64ec06a73f50cae9ee49035c840710d87777d50f`.
- Candidate source commit: `799dd763e5a20de29d78fdd1dd26f7a5a5f0b0b0`.
- Baseline verifier SHA-256: `39f4669cff094c5c25f3d416d95084b108c139c12e0ed4ccba14988b9478ccf8`.
- Candidate verifier SHA-256: `c2995353ec6a5f82dec94717699daec9792a755a028bfdac8897f87136eec53d`.
- Harness SHA-256: `79c1dea750cc704049d0a5cea91af54d80932a56a49a0e965a409cba68c50cdb`.
- Ordinary fixture RED: `4aaf2b2211163a25bf84c438514d0b9a381def81`.
- Ordinary fixture GREEN: `666310b877d004b501151058b53a7417f84a2f42`.
- Ordinary fixture patch SHA-256: `a235c78f3692aa456765ffac8e6187bd4cae55099a9eef82bb83da51a821afba`.

Raw evidence: `baseline-results.json`, `candidate-results.json`, `baseline.log`,
`candidate.log`. JSON includes complete per-case observations and actual JUnit
hashes. JUnit bytes themselves are removed by the production profile's cleanup;
the challenger did not change its behavior to retain them. This evaluation does
not establish APK/device/model quality, cryptographic worker authenticity, or
hostile-code OS isolation. Independent QA retains the final integration gate.
