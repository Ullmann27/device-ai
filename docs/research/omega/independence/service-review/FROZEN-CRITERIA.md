# Independent service acceptance — frozen before candidate inspection

Date: 2026-11-03. Baseline: `527d43f93f6b1837bd8da38cfefcfc84a84fd464`
(tested production source `1595389274acb840c4ab544fa699c9eb2a04bd53`).
Reviewer: `independence_service_challenger`. Builder: separate
`independence_service_builder` session. No candidate implementation or proposed
API has been read at this freeze. The existing `local-teamleader-loop.mjs`,
CCC1 recovery/worker, journal and prior project rules were inspected.

## Hypothesis and boundary

A local owner can control a persistent coding service without a cloud developer
agent. Existing CCC1 task/grant/receipt authority and the existing worker loop
remain authoritative. New transport/daemon code may not silently reinterpret an
unknown prior execution as a task that is safe to repeat.

This review tests host lifecycle/admission with real OS processes and explicitly
labelled fixture work where necessary. Fixture model answers are not neural
evidence. A separate genuine approved-Qwen run must prove model/patch/verifier/
report continuity. No Fold7, signing, release or general intelligence claim is
in scope. No new model, network account or paid resource is required.

## Frozen acceptance cases

| ID | Trigger / counterexample | Required observation |
|---|---|---|
| S01 | Start valid service, request status, then stop | Ready status comes from the current live instance; stop terminates its worker and releases ownership. |
| S02 | Two concurrent starts for the same service state | At most one worker/active inference admission; loser cannot overwrite the winner's lock/status or kill it. |
| S03 | Stale metadata refers to an unrelated live PID / changed instance identity | Refuse or classify unknown; never signal the unrelated process. |
| S04 | Read a persisted RUNNING heartbeat after its process died | Do not present the old heartbeat as a live healthy service. |
| S05 | Queue reaches its hard limit; concurrent submissions race at the last slot | Atomic bounded admission; no hidden overflow, dropped accepted order or multiplication of budgets. |
| S06 | Re-submit an identical owner idempotency key; reuse key with changed task/source/budget | Same request returns same durable identity; conflicting content rejects, including after restart. |
| S07 | Submit incomplete/unreviewed data, forged flags or task outside current grant | No model/tool execution or implicit permission expansion. Owner acceptance is bound to the exact validated request/source and constraints. |
| S08 | Cancel queued task, then restart; deliver late completion for that task | No execution, no resurrection and no learned success. |
| S09 | Cancel an actually running fixture child/model/tool | Canonical cancellation becomes observable; termination is verified or reported unknown. Late success cannot override cancellation. |
| S10 | Kill daemon after dequeue/claim but before definitive result | Restart preserves identity; reconciles canonical state; ambiguous claimed execution is blocked for owner review, not auto-replayed. |
| S11 | Crash before any execution was claimed | Only a provably unstarted task may resume; preserve overall attempt/call/time budgets. |
| S12 | Completed result exists but delivery/checkpoint write was interrupted | Replay verified result only; do not rerun model/tool work. |
| S13 | Source HEAD/root changes, dirty checkout, revoked current grant, or replaced task file | Refuse current execution/results; never rely on historical approval alone. |
| S14 | Malformed/torn/corrupt journal, unsafe state paths, symlinked inbox or artifact | Fail closed or use the existing bounded safe-tail recovery; never overwrite unrelated files or mint success. |
| S15 | Owner starts/stops repeatedly, exhausts cycle budget, or idle service waits | No accumulating child/render/model processes. Idle work performs no model calls; restart cannot reset a consumed task budget. |
| S16 | Builder/verifier share a claimed role, forged verifier flag, copied receipt or stale report | Canonical independent verifier and actual task/job/source/model/workspace bindings decide; presentation never upgrades unverified work. |
| S17 | Controlled accepted task reaches terminal report and fresh service process reads it | Report stays traceable to canonical receipts; private keys and mutable model state never appear in public status/evidence projections. |
| S18 | Run while cloud/API environment variables are absent and outbound calls are observed/denied | Start/control/task execution use only approved local runtime and fixed local executor. No developer agent or cloud call is required. |

## Method and budgets

- Freeze this contract before reading builder API/source; retain its commit and hash.
- A later CLI/API adapter may map commands but cannot manufacture expected results.
  Its transparency is reviewed and the actual daemon/process/journal files are observed.
- Use a temporary synthetic repository/state directory, real Node processes and
  counters/receipts external to the process under test. No real owner data.
- At most two service fixtures concurrently; each fixture has bounded child count,
  output and runtime, a deadline, and verified teardown in `finally`.
- No simultaneous Gradle/native-model benchmark. Small Node process fixtures may
  run after coordination with Chief.
- Preserve every attempted invocation, setup error, assertion RED and final GREEN.
  Missing API/transport is `NOT_RUN` or `BLOCKED`, never a passing control.
- New findings after disclosure are supplemental regression material; no blind
  generalization claim is attached to service-contract tests.

## Baseline findings (not candidate defects)

The existing durable loop has a finite cycle budget, a source-bound checkpoint
and a directory lock. `ccc1-recovery.mjs` already distinguishes NOT_STARTED,
FINISHED_UNRECORDED and INTERRUPTED; claimed unfinished work requires owner
approval. A new daemon must preserve that distinction. Its status transport must
add actual liveness/instance checking rather than treating saved JSON as proof.

Candidate service tests: `NOT_RUN` at freeze. No production file was changed.
