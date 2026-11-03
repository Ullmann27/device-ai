# MΩ-1 workspaces handoff

WIP / UNVERIFIED checkpoint. Base cf34fb6a. EXP-01/02/07/08/11/12.

Scope: extend the existing isolated executor with per-worker/task execution identity,
observed confinement, and a narrow binding to the existing CCC1 journal/recovery.
No replacement scheduler, mailbox or journal. Kotlin ChiefWorkerPool,
ChiefBrainWiring, routines.AgentMailbox and cognitive.ComputeFabric remain authority.
`tools/continuum-virtual-fabric` is an unintegrated prototype, not capacity.

Planned host proof: two real ephemeral Docker workspaces, fixed Node profile,
no network, cross-access negative probes, inspected resource limits, teardown.
No model execution, Android or Fold7 acceptance. No owner data or downloads.

Tests: NOT_RUN. Next: RED contract test, minimal implementation and raw host proof.
Rollback: omit this research branch.
