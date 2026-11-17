# Independent lifecycle acceptance — frozen before candidate inspection

Reviewer: `synapse_lifecycle_challenger`, 2026-11-03.
Baseline: `efe622dda00e620952483dedad19257efb3284e6`.
Subject: existing `ChiefWorkerPool`, not a second scheduler or model runtime.
Candidate implementation has not been inspected. These conditions precede it.

## Acceptance

1. Every accepted running or queued assignment receives exactly one terminal report
   after shutdown; queued factories must not execute after cancellation.
2. Shutdown closes admission: a later submit is rejected or throws the existing
   rejection exception, and never returns accepted without terminal delivery.
3. Submission racing shutdown cannot strand an accepted assignment, capacity slot
   or completion counter. Repeated shutdown cannot duplicate reports.
4. Cancellation before factory completion forwards stop once when a job becomes
   available, never starts that cancelled job, and delivers CANCELLED.
5. Late results after cancellation cannot become DONE or resurrect an assignment.
6. `awaitIdle` includes synchronous report callback completion; it does not claim
   that interrupted external tools have necessarily terminated or effects reverted.
7. Existing callback reentrant submission remains supported while admission is open;
   both same-ID and different-ID submissions retain independent completion accounting.
8. A callback exception cannot lose another report or completion accounting.
9. A job ignoring interrupts must not produce additional physical worker threads
   beyond maxConcurrent, even across sequential timeout/cancellation submissions.
10. Existing pool/scoping regressions still run against the same compiled source.

Tests use latches/barriers and bounded waits, not scheduler-dependent sleeps as the
sole ordering proof. Record actual exit codes and unmodified RED failures. Timing
is only a generous liveness deadline, not a throughput benchmark. At most small
isolated JVM runs; no concurrent Android whole-build. Host tests establish no
Fold7/Gemma/independent reasoning claim. No production code writes by reviewer.

## Decision rule

Any accepted assignment silently dropped, duplicate report, cancellation revival,
unbounded executing workers, callback accounting regression or unsupported success
claim blocks this slice. Candidate and baseline run the same frozen cases. Once
shared with the builder, cases are regression evidence, not future blind holdouts.
