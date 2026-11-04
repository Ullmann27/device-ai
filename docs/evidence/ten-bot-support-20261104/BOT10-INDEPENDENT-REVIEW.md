# Bot 10 — independent review

## Initial review and source-boundary follow-up

The reviewer inspected committed range
`578bdfd12f896cc990bf1a043bc1b248ba3adb6d` to
`9a439a55d957a17f2d785400c967dd15785224eb`, including direct routine consumers,
regression tests and execution receipts. The separately reviewed source-boundary
follow-up is `b368a8c569cba0c07c878c012766fb8fbaa2e346` to
`b6beb349a5ff029f00f40849d538591abf4db59e`. Intervening documentation and the later
WorkerScope change were outside this initial review.

### Findings

- Critical: none demonstrated in the inspected scope.
- Important in the original probe: arbitrary CLI source was executed before
  the probe emitted fixed statements that no model or owner input had been
  used. Those statements were supported only for the previously reviewed
  runner bytes.
- The separately inspected SHA guard closes that finding: it compares the
  already-read bytes before compilation or execution, and executes those same
  bytes afterwards. The reviewed regression and receipts show RED (one error)
  and GREEN (one passing test), including rejection without creating the
  fixture's marker file.
- Minor: no additional demonstrated finding in this scope.

The reviewer found the routine snapshot repair consistent with its direct
dispatcher consumer, which does not need to mutate the admitted scope. The
overflow guard is valid for the existing positive-period admission constraint.
No new permission, scheduler, package, signer or update-channel change appears
in the inspected production diff.

### Evidence assessment and verdict

The initial receipts correctly distinguish 45 baseline Node tests, 8 update
policy tests, routine RED 12/3 and GREEN 12/0, and Android compilation with 31
selected passing tests. The 31 include the 12 routine tests. The 13 additional
service cases remain BLOCKED by fixture prerequisites. The reviewer inspected
source and receipts and did not independently rerun these tests.

Verdict: the isolated routine changes together with the SHA guard are suitable
for further integration review. The original unbound probe alone is not. This
does not authorize a main merge or release, and it does not establish any
behavior of the changed foreign weight runner. WorkerScope and device/APK
acceptance remain separate checks.

## WorkerScope follow-up

The reviewer separately inspected the uncommitted WorkerScope diff against
`ebc1c54e8630ce0b79379604f93992f53baf06e1` and the six new admission/adapter tests.
The following hashes matched the explicitly assigned source:

- `ChiefWorkerPool.kt`: `faadbdd88555454b7d4a720fc7fba49148baa809d6414ba91a81ea9ee2be0d16`.
- `ChiefWorkerScopeAdmissionTest.kt`: `3b1b8c2765a65447379a2e4d580d19ef53d1f240ad7151321e54d2d0b0cfb31d`.

Critical, important and minor: no demonstrated findings in this bounded review.
The pool copies tool scope before publishing the admitted assignment; direct
model and runner adapters independently snapshot their scope. The existing
`ChiefJobs.kt` wiring still uses ToolGate, policy and ApprovalProvider. Cancel,
shutdown, capacity and report code are unchanged. The latch-based tests hold a
queued job until after its caller mutates the original set, and also cover both
direct adapters. RED XML confirms six expected failures. The two recorded RED
runs repeat the same six cases and are not additive coverage.

Verdict: suitable for isolated further integration from the perspective of this
code review. GREEN was still pending when the reviewer completed this follow-up;
the coordinator's subsequent execution receipt is the test gate. The reviewer
did not execute tests. No other changes, main merge, release or device acceptance
are included in this judgment.

The coordinator subsequently verified the GREEN receipt: Android production and
unit-test Kotlin compilation ran, and all 37 selected tests passed with zero
failures, errors or skips. The reviewed file hashes remained unchanged.
`bot09-green-summary.json` and its raw XML/logs record this later execution; it
is not represented as an additional test run by the reviewer.
