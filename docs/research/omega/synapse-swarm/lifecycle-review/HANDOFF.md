# Independent lifecycle and queue review

Reviewer: `synapse_lifecycle_challenger` (separate from the implementation agent).
Branch: `codex/synapse-lifecycle-review-20261103`, draft PR424.
Only reviewer harnesses, criteria and evidence are changed in this branch.

## Source-bound findings

| Source | Independent shutdown cases | Existing pool cases | Retention follow-up |
| --- | --- | --- | --- |
| efe622dda00e620952483dedad19257efb3284e6 | 6/8, two real failures | 9/9 | FAIL: 24 retained cancelled tasks |
| 7e94529e7d327658643ccc51c715b871ae3d8e1e | 8/8 | 9/9 | FAIL: same preexisting retention |

All listed cases actually ran with zero skips. No local model, Android device or
OS-isolated tool execution was performed by these tests. `awaitIdle` reports
completion delivery, not proof that an interrupt-resistant native call terminated.

The initial shutdown slice is independently accepted: closed admission and a
single owner of each supervisor/report prevent the executor from silently losing
queued assignments. Original independent RED logs are retained. The subsequent
queue retention finding is preexisting and **not** introduced by that fix.

The Chief authorized a focused resource follow-up. Its candidate is still pending
at this checkpoint. No full resource-bound acceptance is asserted yet.

## Freeze and executable evidence

* Initial criteria: ecf40dd9693d19293cac71ccd9e658150e919c15.
* Eight independent executable cases: fa03df7464dd6ead84751250925523b2d6f7d8fc.
* Additional retention diagnostic froze in e3f77ef1 before its first execution.
* Follow-up acceptance froze in 73e6f1ef before inspecting the resource repair.
  Rejection is allowed when physical capacity is unavailable; the test does not
  force extra accepted work. Counts, terminal delivery, recovery and no replay
  are checked independently.

Reproduce from this reviewer checkout:

```
bash tools/synapse-lifecycle-review/run.sh /path/to/candidate /path/to/new-evidence
bash tools/synapse-lifecycle-review/run-retention.sh /path/to/new-evidence /path/to/new-retention-evidence
```

The narrow harness recompiles the exact candidate `ChiefWorkerPool.kt` and tests.
Other application types come from the previously built real application dependency
graph at `/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes/debug`
(source 7247e99d), with official Kotlin/JUnit/Android dependencies. No stubs or
substitute scheduler were used. ChiefJobs/AgentToolLayer/BoundedAgent source remains
byte-identical between that source and the review baseline. This is not a fresh
whole-app compilation; final combined Gradle remains Chief/QA responsibility.
Run directories retain actual source SHA, input SHA256, compiler logs, JUnit logs
and exit codes. Compiled classes are temporary and deliberately not committed.

## Remaining boundaries

No claim about model learning, MΩ-1 cognitive acceptance, full workspace isolation,
native stop compliance, UI lifecycle or Fold7 thermal/RAM/GPU performance follows
from this slice. A runtime ignoring cooperative stop still needs its original
poststate/receipt check. Production, main, signing, owner data and installation
were untouched. Quota remains NOT_VISIBLE; checkpoints are pushed regularly.
