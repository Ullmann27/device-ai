# Tuning bot 3: existing runtime/worker selection

Base: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`, own branch `tuning/workers-59ad`, coordinated draft PR #511. Existing EXP-01 and EXP-11 interfaces remain compatible.

Only production change is the linear best-task selection in `nextEligible()` in `.github/continuum/development-backlog.mjs`. No other runtime, state transition, lock, cancellation, permissions or schema code changed. `ChiefWorkerPool.kt` and `routines/ContinuumRoutines.kt` remain allocated to separate work.

Verified on Linux/Node v24.22.1: 52/52 existing and targeted tests pass; 30,720 source-bound parity cases pass exact task-object identity with frozen valid inputs. Formal timing is pending the coordinator's benchmark slot; no speedup is asserted at this checkpoint. Android/Fold7/APK tests are NOT_RUN.

Full commands, raw evidence, limitations and rollback are in [workers/README.md](../../evidence/three-tuning-bots-20261104/workers/README.md). Reproduction harness: `tools/tuning-workers-20261104/benchmark-backlog-selection.mjs`.

Next step: run the prepared paired timing harness only after the coordinator releases the CPU slot, record results and source hashes, then provide qualified commits to root for publication. No main/production merge or release. Revert this isolated tuning commit to restore the previous selector; no data migration or deletion is needed.
