# Synapse lifecycle repair — first-wave freeze

Baseline: `efe622dda00e620952483dedad19257efb3284e6`.
Scope: existing `ChiefWorkerPool`, its tests, and reproducible host evidence.
Experiments: EXP-01 / EXP-07 / EXP-11 / EXP-12.

Hypothesis: stopping the existing pool can discard queued supervisors without
terminal delivery. Its unfinished counter and visible active IDs then remain stale.
An explicit shutdown/admission boundary and exactly-once queued cancellation can
fix this without creating a new scheduler or allowing additional work.

Acceptance before implementation:
1. Hold one factory, enqueue two jobs, shut down; queued jobs receive one CANCELLED
   report and never invoke their factory. Release the held factory; all accepted
   assignments complete their report callbacks and awaitIdle succeeds.
2. Shutdown invoked by a completion callback also closes queued jobs without a
   deadlock. Repeated shutdown is idempotent.
3. Submission after the shutdown boundary retains RejectedExecutionException and
   does not increment unfinished work or dispatch a job.
4. Cancel/start and shutdown/start races do not duplicate callbacks, restart jobs,
   or turn a cancelled late result into DONE.
5. Existing nine pool tests, including reentrant same-ID callbacks and throwing
   callbacks, remain unchanged and pass.
6. Reports are cancellation requests, not proof that an interrupt-resistant native
   call stopped. No on-device, model-inference or persistent-recovery claim.

Use CountDownLatch barriers rather than timing-dependent sleeps for the defect.
Budget: focused Kotlin/JUnit host compilation, one small JVM at a time, no full
Gradle or model jobs. Freeze tests first; retain baseline failure logs. A separate
challenger reviews the candidate and Chief owns combined-source acceptance.

Status at freeze: SOURCE_UNCHANGED, TESTS_NOT_RUN, FOLD7_NOT_RUN.
No package, signatory, original sphere, avatar, ToolGate, memory or owner data change.
