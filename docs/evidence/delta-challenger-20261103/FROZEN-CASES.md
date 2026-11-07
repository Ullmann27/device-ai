# DELTA independent challenger — frozen cases

Status: FROZEN BEFORE CANDIDATE INSPECTION; implementation/results NOT_RUN.
Baseline: `64ec06a73f50cae9ee49035c840710d87777d50f`.
Only baseline verifier, its existing tests, and repository instructions were read.
Candidate PR, implementation, handoff, and prior outcomes were not read.
Scope: existing CCC1 detached-worktree fixed Node/JUnit profile. EXP-02/07/11.

The following oracle is independent of candidate reason strings. Each transition
uses a real temporary Git repository, unchanged tests across RED→GREEN, and a
source-only patch. Receipts initially claim the observations returned by the
profile under test; selected claim mutations are then applied. Both commit and
patch entry points are exercised where relevant. No mocked runner is acceptance
evidence. Source and test SHA-256, fixture commits, verdicts, run counts and reasons
will be retained. Caller HEAD/status/worktrees must remain unchanged.

| ID | Frozen scenario | Required outcome |
|---|---|---|
| D01 | Ordinary arithmetic repair with retained sibling | VERIFIED |
| D02 | Genuine repair additionally enables one source-driven test | VERIFIED (coverage extension is legitimate) |
| D03 | Repair drops a previously executed passing sibling via a source flag | REJECTED |
| D04 | Repair replaces that sibling with a new passing name, keeping total count | REJECTED |
| D05 | Repair changes passing sibling to SKIP via source flag | REJECTED |
| D06 | Repair changes passing sibling to TODO via source flag | REJECTED |
| D07 | Two identical testcase-name occurrences; source reduces occurrence count | REJECTED; no Map overwrite may hide loss |
| D08 | Two distinct nested suites have same leaf; one leaf disappears after repair | REJECTED |
| D09 | Nested-suite siblings with identical leaf names are all retained | VERIFIED if canonical identity disambiguates; conservative refusal is reported as a compatibility limitation |
| D10 | Different directories contain same filename stem and leaf; one disappears | REJECTED |
| D11 | An ambiguous classname alias names multiple possible targets | REJECTED |
| D12 | Unique alias identifies a real failing target and repair retains coverage | VERIFIED |
| D13 | Two receipt target strings alias the same underlying test | REJECTED |
| D14 | Honest repair with one pre-existing skipped sibling | VERIFIED if unchanged skip policy is supported; otherwise report limitation |
| D15 | Honest repair with a pre-existing TODO sibling | VERIFIED if unchanged TODO policy is supported; otherwise report limitation |
| D16 | False red or green exitCode, command, timedOut, cancelled, skipped or passedTargets claims, one field at a time | REJECTED; malformed arrays produce rejection rather than an uncaught error |
| D17 | Repair-mode receipt asserts merge/install/device authority or wrong schema/version | REJECTED |
| D18 | Malformed/truncated JUnit, absent report, zero-case report | Fail closed, never VERIFIED; parser-only probes explicitly separate from end-to-end evidence |
| D19 | Real fixed-profile timeout followed by a fresh ordinary run | Timeout rejected; fresh run succeeds with caller state preserved |
| D20 | Wrong patch hash, patch edits tests, absent/missing target | REJECTED |

Implementation details may adapt to observed Node 24 JUnit syntax, but scenarios,
accept/reject criteria and independence boundary remain frozen. Any case impossible
through the actual fixed profile is marked NOT_APPLICABLE or parser-only, not
silently replaced by a fake execution. The verifier is not an OS sandbox and
cannot authenticate arbitrary worker text; attacks requiring hostile global host
state or arbitrary worker-authentication guarantees are outside this contract.

First assessment is bounded to 20 minutes; full integration belongs to independent
QA. No production files, workflows, weights, release/signing/install or owner data
are changed. Rollback is to omit this test/evidence branch. Quota: NOT_VISIBLE.
