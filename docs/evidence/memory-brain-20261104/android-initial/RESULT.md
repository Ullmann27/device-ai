# Initial complete Android gate — retain failure

Exact clean source: bfc427c6d76f08bbe56513c330342be3be997900.
Offline Gradle compiled production and unit-test sources successfully, then ran
**2,442 tests: 2,422 passed, 1 failed, 19 skipped**. The task stopped before lint.
The complete command, elapsed time and unchanged source identity are in run.json.

The failure was RetrievalCalculationSourceTest.nothingAfterCloseAndThrowingListenerIsIsolated:
its final assertion expected the dispatch worker reference to be null. All
actual event-delivery assertions passed. Both this test and its source were
unchanged from baseline 578bdfd1. Closing the last listener wakes a worker whose
reference is cleared asynchronously, while awaitIdleForTest only waits for
queued/in-flight events. Therefore the final assertion races worker shutdown.

This failure is not deleted or reported as a successful run. A bounded wait on
the actual worker termination, preserving the original assertions, is being
checked separately before another full run. No production fallback or skipped
test is introduced to obtain a pass. Lint: NOT_RUN in this initial invocation.
