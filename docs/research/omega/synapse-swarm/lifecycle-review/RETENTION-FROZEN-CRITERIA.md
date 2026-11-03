# Follow-up resource criterion, before repair inspection

Baseline `efe622dda00e620952483dedad19257efb3284e6` and shutdown candidate
`7e94529e7d327658643ccc51c715b871ae3d8e1e` both retain 24 cancelled FutureTasks
after 25 accepted assignments with maxConcurrent=1/maxQueued=0, while the sole
physical call ignores interruption. This is a preexisting issue, separate from
the accepted shutdown repair. The first diagnostic was frozen in `e3f77ef1`.

The Chief authorized a bounded follow-up. Acceptance remains independent:

* New submissions may be rejected to preserve resources. The diagnostic must not
  require accepting extra work when physical execution capacity is unavailable.
* Every accepted assignment still receives exactly one correlated terminal report;
  no DONE result is claimed for a job that did not execute.
* Retained worker tasks, including cancelled stale tasks, cannot grow beyond the
  configured total logical capacity during a sequence of timeouts/cancellations.
* No replacement native threads beyond maxConcurrent, no queued cancelled task
  replay after the original physical call returns, no extra accepted execution.
* A fresh job can run normally once the physical call exits and capacity is free.
* The original eight tests and existing regressions remain unchanged.

This strengthens the resource evidence; it does not establish physical termination,
OS isolation, GPU/RAM budgets, model inference or Fold7 acceptance.
