# Code48 training diagnosis and cancellation repair

Status: LOCAL_ONLY / NOT_PUSHED. Parent `79e614134b30113272c76d51db6363f15649c16f`.
EXP-02 / EXP-07 regression repair; EXP-04 / EXP-05 / EXP-12 checkpoints, holdouts and learning contracts preserved.

## Reproduced current defect

The actual `ContinuumAutonomousJobService` can wait for `ContinuumCdxLargeLab.status()` after calculating the remaining budget. A timeout or owner pause during this wait makes `lab.next(cancelled)` throw at its cancellation precondition. The existing CDX catch classified this as `FAILED`, causing a training-failure notification. The OFFLINE completion also reported an owner pause as `DEFERRED_TIME_BUDGET` even when no deadline expired.

The repair consults the existing cancellation/resource reason: budget/resource stops end as `DEFERRED` with the original fixed reason; owner/Android cancellation ends as `INTERRUPTED`. Real exceptions while the job remains permitted still end as `FAILED` and retain the existing JobScheduler retry. No training step, thermal limit, timeout, model parameter, holdout threshold or signature was relaxed.

## Exact executed evidence

- Same final test source, actual Android JobService, real existing locks and preferences, synthetic local files: RED 3 tests / 2 failures; GREEN 3 tests / 0 failures.
- Lock contention + 16 s elapsed time: CDX and OFFLINE both `DEFERRED / TIME_BUDGET`; owner pause: both `INTERRUPTED`. SWB1 bytes, synthetic confirmed fact and CDX stage cursor remain unchanged.
- Negative control: a real file obstructing the learning directory still produces `FAILED` and requests retry; the obstructing file remains unchanged.
- Existing 29 tests freshly passed: practice training, extended curriculum, model preservation, cancellation, thermal/storage/battery budgets, public-source outcomes and audit-write failures.
- Android SDK36/Kotlin 2.4.0/JDK17 compile PASS; runtime Robolectric SDK28. Cached dependency classes are from `6a6f885f952784c79b209ad39b20d14ccf014df8`; the scheduler source was verified identical before this patch. GREEN overlays the freshly compiled scheduler. Raw commands, logs and hashes are in `docs/evidence/code48-training-cancellation-20261107/`.
- Full integrated Gradle/APK and physical Fold7 training/temperature: NOT_RUN in this subtask. The owner's current failure is not established to be this reproduced race.

Portable full-Gradle follow-up (not the focused invocation already executed):

```sh
gradle --no-daemon --max-workers=2 --console=plain \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumStandaloneVersionCode=48 \
  -PcontinuumUiWidgetTests=true -PcontinuumLocalStandaloneQa=true \
  :app:testDebugUnitTest --tests at.heinz.deviceai.learning.ContinuumTrainingCancellationUiTest
```

## Remaining diagnosis boundaries

- Fresh [issue #76](https://github.com/Ullmann27/device-ai/issues/76) describes the historical PUBLIC audit-write error from 22 October, version 0.3.12. It is not proof of a current Gemma/LM1 training fault. Current `ContinuumStudyAuditWrite` already distinguishes `STORAGE_FULL`, `NOT_WRITABLE`, `COMMIT_FALSE` and stop requests, and retries the Android interrupt-flag case. Its six tests passed again.
- PUBLIC uses a 90 s round budget; OFFLINE uses 15 s. Shared resource policy defers below 20% battery, above thermal status 1 or below 128 MiB storage. `REJECTED` means the candidate failed the quality gate; it must not be promoted just to make training appear successful.
- A remaining device report needs installed version, channel (PUBLIC/OFFLINE/CDX/NEURON), exact phase/detail and timestamp. These existing monitor fields distinguish fetching, checkpoint/audit persistence, resource deferral and weight-training exceptions without exporting private learning data.
- [PR #511](https://github.com/Ullmann27/device-ai/pull/511), head `39ff86e7c2a8a8f5b03b90ce15a6a3e98f265c21`, contains missing LM1 tokenizer/hex and softmax scratch-allocation reductions. The active app has no caller outside `learning/lm` to `LmRuntime`/`LmModel`/`LmTrainer`; actual PUBLIC/OFFLINE paths use `ContinuumLearningCore` and optional `ContinuumCdxLargeLab`. The three LM1 changes were deliberately not ported as a purported fix for the owner's training or overheating. Existing current `AiContextCompiler` protections remain intact.

Next: coordinator integrates this commit, runs the exact combined source gates and reviews actual device status. Rollback: revert this commit; no stored-data migration or checkpoint rewrite is involved.
