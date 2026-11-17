# DELTA ChiefWorkerPool completion repair

Status: LOCAL_ONLY / NOT_PUSHED. Chief requested local commits plus a bundle after
shared GitHub CLI credentials expired. No token read, replacement, or push attempt
was made. Root coordinates connector publication. No PR has been created here.

- Base: `853a8ee08359ce41375ddb9334ac8ae9c2e2c59e`.
- Branch: `codex/delta-pool-completion-20261103`.
- Worktree: `/workspace/delta-pool-completion` (clean).
- Final source: `f61fc3d56b9437d762b78625f748e64f887ef5f6`.
- Bundle: `/workspace/delta-pool-proof/delta-pool-completion.bundle`.
- Bundle SHA-256: `70cc498f3c6d18d8de504b64d267cdc6dfb828b688b88983f2c496b9913c081b`.
- Only owned production/test files changed: `ChiefWorkerPool.kt` and
  `ChiefWorkerPoolTest.kt`. No other branches or Momega work were touched.

Root cause: `report()` removed its slot before invoking the synchronous report
callback. `awaitIdle()` checked only the slot map, so a caller could drain its
mailbox before the callback published anything. The deterministic test enters
the callback, blocks it on a latch, then expects `awaitIdle(0)` to return false.
No added sleep, retry, relaxed timeout or weakened delivery assertion is used.

Final repair: an atomic unfinished-assignment counter increments during admission
and decrements in `finally` after report delivery. `awaitIdle()` checks that single
counter. Slots still release before callbacks, preserving callback submission at
maxConcurrent=1/maxQueued=0 for both the same ID and a new ID. Reentrant submission
increments before the old callback decrements, leaving no false zero between the
assignments. No callback runs under a pool monitor. A rejected `runners.execute`
rolls back its slot and counter, then rethrows the original
`RejectedExecutionException`; no rejection is silently converted to success.

Frozen tests and actual execution:

1. `90c73257`: original latch and throwing-callback tests frozen before production
   edits. Baseline 7 tests / 1 failure, exactly the blocked callback `awaitIdle(0)`
   assertion (`red-junit.log`).
2. Chief review required legacy callback capacity. `8d6ed02c` froze same/new ID
   resubmission positives before the counter implementation. The implementation
   detail assertion that `active()` must retain a callback's slot was removed to
   preserve the original capacity contract; the `awaitIdle(0)` delivery oracle was
   unchanged. Baseline 8 tests / 1 failure, the same delivery assertion
   (`red-final-junit.log`).
3. `3fb750bd` froze submit-after-shutdown cleanup. The counter-only intermediate
   implementation showed 9 tests / 1 failure at retained rejected capacity
   (`admission-red-junit.log`).
4. Final `f61fc3d5`: **9/9 focused pool tests PASS**, including callback delivery,
   throwing callback exactly-once/ID reuse, same/new-ID reentry, rejected admission,
   existing cancellation, timeout and tool-scope checks
   (`accepted-final-junit.log`, exit 0).
5. Existing exact-853 compiled `ChiefBrainWiringTest`: **4/4 PASS** against the
   freshly compiled final pool (`accepted-final-wiring.log`, exit 0).
6. Separate small Kotlin probe: **2/2 PASS** for callback reentrant submit/cancel
   and callback-triggered shutdown (`accepted-final-callback-probe.log`, exit 0).

All compilations used official Kotlin 2.4 dependency jars with 384 MiB maximum
compiler heap and actual exact-853 QA classes supplied by independent Quality.
No Gradle task or complete Android build was run. Compiler/test commands, exact
source/test SHA-256, exit codes and dependency classpaths are in the binding JSON
files. `accepted-final-binding.json` and `accepted-final-*` logs are authoritative
for the shipped implementation; earlier logs retain superseded intermediate
implementations openly. `SHA256SUMS` hashes the evidence and bundle.

Observed callers: RoutineDispatcher publishes its mailbox message synchronously
inside onReport. AgentActivity posts UI work and does not wait for the pool in its
callback; onDestroy shuts down without joining. `awaitIdle()` covers synchronous
callback completion, not asynchronously posted UI work. Calling awaitIdle from
inside one's own callback remains a self-dependency and returns false on its
timeout; no existing production callback does that.

Scope boundary: the pre-existing `shutdownNow()` handling of runner tasks still
queued at shutdown is not redesigned. Such tasks can be removed from the executor
queue before reaching supervise/report; this repair does not claim to fix that
separate shutdown/reporting gap. Full integration and independent review belong
to Quality. No main merge, APK, install, signing, owner data or productive weights.
