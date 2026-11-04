# Coordinator repair — routine scope and scheduling

Date: 2026-11-04. Experiments: EXP-01, EXP-07, EXP-11. Source before repair:
PR #483 `578bdfd12f896cc990bf1a043bc1b248ba3adb6d` (unchanged by the support
documentation/baseline commits).

## Reproduced defects

1. `RoutineBook.add` retained the caller's `allowedTools` set. A caller could
   mutate that set after registration, or mutate a set returned in a claimed
   routine. The registered scope then changed; e.g. `get_battery_info` could be
   replaced or supplemented with `install_apk`. `RoutineDispatcher` passes the
   same set to `WorkerBrief`. This demonstrates broken scope ownership, not an
   observed tool execution or a bypass of the independent ToolGate.
2. An accepted `Every(Long.MAX_VALUE, 0)` interval overflowed when its next due
   time was calculated at `nowMillis=100`. The routine became immediately due
   again after a completed run. This is a deterministic arithmetic edge case,
   not a claim that normal 15-minute schedules currently repeat unexpectedly.

## Changes

The existing routine book snapshots the tool set at admission and exposes an
unmodifiable set in its existing `Routine` value. No API or persistence format
changes. Computation of the next due time saturates at `Long.MAX_VALUE` when the
future timestamp cannot be represented. Existing approval gates, dispatch,
mailbox and retry semantics are unchanged.

## Actual verification

Production `ContinuumRoutines.kt` plus the existing 9-test suite and the new
3-test `RoutineBookAdmissionTest` were compiled with the locally installed
Kotlin compiler/JDK17 and run with JUnit 4.13.2/Hamcrest 1.3.

- RED: 12 executed, 3 expected failures, all 9 existing tests passed, exit 1.
- GREEN: same 12 tests passed, 0 failures, exit 0.
- Raw logs: `routines-red-compile.log`, `routines-red-junit.log`,
  `routines-green-compile.log`, `routines-green-junit.log`.
- Green source hashes: `routines-green-source-sha256.txt`.
- Test class entry point: `org.junit.runner.JUnitCore
  at.heinz.deviceai.routines.ContinuumRoutinesTest
  at.heinz.deviceai.routines.RoutineBookAdmissionTest`.

The production dispatcher was inspected to verify it only reads/passes the scope
and does not require a mutable set. Android Gradle consumer verification and
independent review are subsequent gates; host unit tests are not Fold7 evidence.
No new scheduler, runtime, model, capability or owner permission was introduced.

Rollback: revert the support commit containing `RoutineBookAdmissionTest` and
this bounded `ContinuumRoutines.kt` change. Do not reset other teams' branches.
