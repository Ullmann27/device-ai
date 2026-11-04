# Calculation dispatcher shutdown test race

Base: `bfc427c6d76f08bbe56513c330342be3be997900`.
Only application-source change: five added lines in the existing
`RetrievalCalculationSourceTest.nothingAfterCloseAndThrowingListenerIsIsolated`.
**No production source changed.**

## Original full-suite failure

The original full Android Gradle run completed 2,442 tests with one failure and
19 skipped. Its failure XML is preserved as `original-failure.xml`:

`RetrievalCalculationSourceTest.kt:63`: expected `null`, received the actual
`Thread[continuum-calc-events-7b25ab31,5,main]` dispatcher reference.

Both original delivery-count assertions had passed. The failure was the final
worker-termination assertion. The original production and test files have an
empty diff from `578bdfd12f896cc990bf1a043bc1b248ba3adb6d` to this task's base.
The parent retains the entire failed Gradle run at its original source identity.

## Cause and deterministic reproduction

Closing the last listener removes it, clears the queue and notifies the worker.
The worker must then reacquire the monitor, observe no listeners, clear its
reference and return. `awaitIdleForTest()` waits for queued/in-flight work, not
for termination after an empty queue. Thus a test thread that reacquires the
monitor first can observe idle state and a still-present worker.

`ShutdownRaceProofTest.kt` is a diagnostic outside all application source sets.
It uses reflection solely to hold the existing dispatcher monitor across close
and the original idle/null assertion. This deterministically selects that valid
schedule without altering the production implementation. It compiles and fails
exactly the old assumption. Its `finally` releases scheduling, joins the actual
worker and verifies that the original worker really terminates within five seconds.
Those cleanup assertions pass. No new memory integration tests are loaded into
this diagnostic; the calculator has instance-local state, not the memory hub's
global directory registry.

This is evidence of an existing test synchronization defect, not evidence that
the newly added memory tests broke calculation delivery or shutdown.

## Minimal correction and verification

The existing test now captures the actual worker before closing the listeners,
joins that worker with a five-second upper bound **outside the source monitor**,
and asserts it is no longer alive. Every existing assertion remains, including
both delivery counts and the final null worker reference.

There is no sleep, no change to production timing, no removed assertion, and no
retry-until-green loop. A single prescribed run of the four original tests in
25 fresh suite instances completed **100/100 tests successfully** in 0.584 seconds
of reported JUnit execution. Source and test were compiled fresh against the
frozen original app dependencies. The compiler's existing Kotlin `Object()`
warning is unchanged; the 25 listener warnings are the original deliberate
throwing-listener isolation test, once per suite.

Receipts:

- `calc-shutdown-controlled-red/`: compiler exit 0, one expected diagnostic failure.
- `calc-shutdown-green-25-suites/`: compiler exit 0, JUnit exit 0, 100 tests passed.
- `source-manifest.json`: exact unchanged production and corrected test hashes.

The corrected test is ready for the parent's fresh complete Gradle/lint gate.
This focused result does not replace that gate. No Gradle process, APK build,
release, installation, model change or external publication was started here.
