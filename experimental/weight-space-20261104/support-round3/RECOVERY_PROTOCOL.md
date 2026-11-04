# Prospective real-process recovery experiment

Status at registration: NOT_RUN. Only the native `describe` command has run; it performs zero optimizer updates. `recovery-registration.json` was generated from the compiled worker's actual tokenizer, schedule and target counts. It binds source bytes, native JAR, Java executable and Kotlin standard library. No manually transcribed special-token IDs are used.

Fixture: fresh minGRU, 2,632 parameters, byte tokenizer, dim 8, one layer, MLP 16, context 32, seed 20261104. Four fixed synthetic sequences form two-sequence batches with ten targets each. Learning rate 0.001, warmup 3, cosine schedule 32, weight decay 0.01, clip 1, one worker. The task completes after 24 updates. Source/data identity and the full original native optimizer state are preserved.

## Four fixed scenarios

1. Uninterrupted reference: one JVM, 24 updates.
2. Normal segmentation: three fresh JVMs with eight updates each. All full-state checkpoint bytes and per-update lines must match the reference. Invoking an already completed job must create no new training attempt.
3. Before-checkpoint interruption: commit step 8; run another eight updates; pause the native process before saving; send actual SIGKILL; record exit -9 and an incomplete segment. Resume from committed step 8 and finish step 24. Exactly 32 updates are physically executed, including eight lost/recomputed updates.
4. After-checkpoint interruption: the same sequence, but pause after immutable checkpoint publication and before DONE. Attempt a second writer while the first holds its lock; it must be blocked without creating another attempt. Kill the native process. The orphan step-16 checkpoint must not advance the committed head. Replay from step 8 and finish step 24, again with 32 actual updates and exact final-state identity.

Across these scenarios: at most 112 actual tiny optimizer updates, two deliberately killed native JVMs, no 16M update. Each native process has a 120-second timeout and at most 1 GiB heap. Each job permits at most four attempts, three requested segments per invocation and 24 committed steps. A separate process per completed segment is mandatory. Partial work may be deterministically recomputed; it is never counted as a second committed advancement.

Additional fixed software negatives alter only generated test fixtures: a completed receipt's predecessor binding, a final checkpoint byte, a job identity, and four pre-reserved attempts exhausting the budget. All must block new training. Original fixture bytes are restored after each tamper check; production files and historical evidence are untouched.

## Publication and recovery contract

The runner takes a committed registration SHA, verifies working and committed source hashes, holds a nonblocking OS file lock and passes that descriptor to its native child. It publishes a completion receipt only after zero exit, exact source/data/schedule/start/end records, finite losses and checkpoint hash verification. It validates the full receipt chain and uses native decoding of the last committed state before continuing. Malformed source/receipt/state causes BLOCKED, not automatic repair or training.

Only completed receipts form the job's durable head; a checkpoint without a completed receipt is not adopted. State and JSON publication use no-overwrite hard links with file/directory synchronization on the tested Linux filesystem. A process can be stopped at any point; the last completed prefix remains the recovery boundary. This does not establish Android support, power-loss behavior on another filesystem or indefinite background execution.

The native entry point only runs this fixed synthetic fixture. It is not an authorization route into historical quality studies or arbitrary model training. No quality metric, model promotion, main merge or release is part of this experiment.
