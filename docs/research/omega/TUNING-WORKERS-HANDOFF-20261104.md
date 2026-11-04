# Tuning bot 3: existing runtime/worker selection

Base: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`, own branch `tuning/workers-59ad`, coordinated draft PR #511. Existing EXP-01 and EXP-11 interfaces remain compatible.

Only production change is the linear best-task selection in `nextEligible()` in `.github/continuum/development-backlog.mjs`. No other runtime, state transition, lock, cancellation, permissions or schema code changed. `ChiefWorkerPool.kt` and `routines/ContinuumRoutines.kt` remain allocated to separate work.

Verified on Linux/Node v24.22.1: 52/52 existing and targeted tests pass; 40,960 final source-bound parity cases pass exact task-object identity with frozen valid inputs. A complete 11-round alternating baseline/candidate comparison at valid 0/1/20/100/500 task sizes passed within the coordinated CPU slot. At 500 mixed-priority tasks, medians are 81.6812 → 15.4194 µs, paired factor 5.258, and comparison counts 3,833 → 499. Strong filters show no general timing win; the 500-task scoped factor is 0.989, and no-match controls include factors below 1. Android/Fold7/APK tests are NOT_RUN.

Full commands, raw evidence, limitations and rollback are in [workers/README.md](../../evidence/three-tuning-bots-20261104/workers/README.md). Reproduction harness: `tools/tuning-workers-20261104/benchmark-backlog-selection.mjs`.

Production source commit is `0e0ba1b`; the final evidence and corrected harness are its documentation/tooling follow-up. All initial raw results remain available with their method limitations documented. The timing slot is released and no more CPU runs are planned. Next step: root reviews and publishes these isolated commits through the coordinated draft PR. No main/production merge or release. Revert the isolated tuning commit to restore the previous selector; no data migration or deletion is needed.
