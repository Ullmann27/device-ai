# LM1: four verified continuity milestones

This sequential support round is complete in draft PR #530. It extends #514 while leaving production sources and active model selection unchanged.

| Milestone | Evidence | Practical effect |
| --- | --- | --- |
| Invalid-update protection | Original 10/34; isolated repair 34/34; ten valid update fingerprints unchanged | Rejected numerical inputs preserve weights, step and future optimizer behavior |
| Complete training checkpoint | 29 native checks; 24 uninterrupted updates equal 8 + restore + 16 | Optimizer moments, schedule and data position survive checkpoint recovery |
| Real process interruption | 38 assertions; two native JVMs killed with SIGKILL and recovered | Last completed state survives; lost work is bounded and replayed; competing writer rejected |
| Actual 16M format size | All 16,011,776 weights and 32,023,552 fresh-zero moments checked | The full-state format stores and decodes the existing model size without changing inputs |

Read [RESULTS.md](RESULTS.md) for exact measurements, limits and commands; [STATE.json](STATE.json) for compact continuation state. Prospective plans: [initial plan](PLAN.md), [process-recovery protocol](RECOVERY_PROTOCOL.md), [16M state-only protocol](LARGE_STATE_PROTOCOL.md).

## Deliverables

- `optimizer-atomic.patch`: first isolated correction, bound to the M1 original/candidate comparison.
- `training-continuity.patch`: combined candidate patch against the original pinned `LmModel.kt`; includes M1 plus state export/restore and trainer synchronization.
- `LmTrainingState.kt`: separate research `LM1T` checkpoint codec. Existing `LM1C` weight format stays unchanged.
- `LmSafetyChecks.kt`, `LmContinuityChecks.kt`: native correctness and integrity controls.
- `LmTrainingFixture.kt`, `LmSegmentWorker.kt`: fixed native tiny fixture and standalone bounded segment entry.
- `run_recovery.py`: committed-source verification, single-writer lock, immutable receipt chain, bounded segment recovery.
- `register_recovery.py`: derive source and actual native tokenizer/schedule bindings before execution.
- `test_recovery_processes.py`: actual JVM interruption and replay experiment.
- `LmLargeStateProbe.kt`, `measure_large_state.py`: actual 16M state-format/resource boundary, with zero updates and fresh zero moments.
- `build_native.py`: isolated compilation from four hash-pinned original Kotlin files.

## Acceptance boundaries

Training continuity is demonstrated on the **2,632-parameter software fixture**. The **16M test performs zero updates**; it checks scale and integrity of the same codec with fresh moments. No improved language quality or recovered historical 16M training trajectory is claimed. Android filesystem/lifecycle, nonzero 16M continuation and application integration remain untested.

The failed quality acceptance in #508 is unchanged. Its consumed final data were not read or reused for tuning. #525 already owns separate capacity/choice-gate integration. This branch creates no APK, main merge, model promotion or indefinitely running service.
