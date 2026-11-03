# Chief worker shutdown — implementation handoff

Date: 2026-11-03. Branch: `codex/synapse-lifecycle-20261103`, draft PR425.
Baseline: `efe622dda00e620952483dedad19257efb3284e6`.
First shutdown source: `7e94529e7d327658643ccc51c715b871ae3d8e1e`.
Final production source: `8eddcdb5b307cbab9c1e3bbcfdf37bbac8637cd7`.
Status: SOURCE_IMPLEMENTED / HOST_TESTED. Independent challenger pending.
MΩ-1 local inference, Android SDK combined acceptance and Fold7: NOT_RUN here.

## Actual change and connection

`ChiefWorkerPool` is the existing Chief execution pool. `shutdownNow()` previously
discarded queued supervisor Runnables, leaving accepted IDs and unfinished counters
without terminal reports. The pool now closes admission before cancellation,
atomically claims each never-started supervisor and emits one CANCELLED report.
Running supervisors retain their own reporting responsibility. Slot removal remains
before callback delivery, and unfinished accounting remains after delivery. This
preserves existing reentrant callbacks during ordinary operation. A closed pool
rejects reentrant/new admission with the existing RejectedExecutionException contract.
Cancellation and terminal report selection are serialized on the slot so a cancellation
that wins that boundary cannot be changed into a late DONE report.

The independent follow-up also reproduced retained cancelled FutureTasks behind an
uncooperative native call. Workers now use a bounded ArrayBlockingQueue with at most
`maxConcurrent + maxQueued` pending entries, fixed `maxConcurrent` threads and rejection
on physical queue saturation. Every cancellation/timeout removes its cancelled queued
FutureTask. No replacement thread is created to bypass a still-running native call.

No registry, mailbox, tools, permissions, models, persistence or alternate scheduler
was introduced. A queued cancellation consumer is called synchronously by shutdown;
callbacks must remain bounded, just as existing report and cooperative cancel hooks.

## Frozen tests and actual observations

Author acceptance was frozen at `9ca3daeb9603756096dfcf191dd11cec18a815ee` and
four executable regressions at `a3026d21` before implementation. Independent reviewer
criteria separately froze at `ecf40dd9`; the reviewer received the exact candidate.

Command (from this worktree):

```
python3 tools/synapse-lifecycle/run-focused.py efe622dda00e620952483dedad19257efb3284e6 docs/evidence/synapse-swarm/lifecycle/red-efe622dd
python3 tools/synapse-lifecycle/run-focused.py 7e94529e7d327658643ccc51c715b871ae3d8e1e docs/evidence/synapse-swarm/lifecycle/green-first
```

Actual official Kotlin compiler + JUnit4, Java21 host execution, 384MiB JVM.
Production pool source and tests are compiled afresh against previously built real
app classes from verified Chief7247e99d (no substitute API stubs). Inputs, hashes,
commands, exits and raw output are retained in each output directory. Full fresh
Android dependency graph acceptance belongs to Chief's final combined build.

* Baseline: compile exit0; JUnit13 run,10 passed,3 failed,0 skipped.
* Candidate: compile exit0; JUnit13 run,13 passed,0 failed,0 skipped.
* Nine preexisting pool tests stayed unchanged. New failures covered discarded
  queued jobs, shutdown from a completion callback and terminal callback admission.
* A held factory demonstrates that shutdown does not invoke queued factories.
* An interrupt-resistant job demonstrates that a cancellation report is delivered
  while its native work may still be running. Releasing that work later produces
  no second report or success. This is an explicit limitation, not termination proof.

Separate resource follow-up (not counted as original blind acceptance):

* Independent original diagnostic `e3f77ef1`: 25 accepted jobs, 24 retained queue
  entries, one physical native call. Reproduced on the first shutdown candidate.
* Author regression frozen at `fa91e3d9` before resource repair: 14 tests run against
  source7e94529e,13 passed,1 failed;12 cancelled entries retained after13 submissions.
* Source8eddcdb5: same14 author tests pass,0 fail,0 skip; cancelled queue size0 and
  physical calls1. Logs: `red-retention-7e94529e/`, `green-bounded-queue/`.
* Reviewer strengthened follow-up criteria froze separately at `73e6f1ef` before
  seeing the resource repair; independent results pending at this document revision.

Reproduce the latest author comparison:

```
python3 tools/synapse-lifecycle/run-focused.py 7e94529e7d327658643ccc51c715b871ae3d8e1e /tmp/synapse-retention-red
python3 tools/synapse-lifecycle/run-focused.py 8eddcdb5b307cbab9c1e3bbcfdf37bbac8637cd7 /tmp/synapse-retention-green
```

## Limits and next action

`awaitIdle` measures completed report delivery, not termination of uncooperative
native calls or process isolation. Thread count and worker queue are bounded; this
increment does not certify per-process CPU/RAM limits or general cancellation of
external/native effects. Cooperative cancel/report callbacks must remain bounded.
No persistence/restart or model-based task was exercised by these lifecycle tests.
No timing speedup, learning benefit or Fold7 claim is made.

Next: independent challenger, then rerun affected Chief/Workspace/Routine tests on
the actual integration SHA and the combined Android graph. Revert only the pool
patch to roll back; there is no persisted data format to migrate or restore.
