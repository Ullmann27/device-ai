# LM1 actual 16M native one-update resource probe — R2 v1

## Registration and scope

This supplementary resource probe must be committed, together with its Kotlin source, to the official research branch **before** execution on the actual checkpoint. Root owns that execution. Tiny fixtures are permitted before registration. The production LM1 source is unchanged at `30cd3a89810ed14fa84eca95236d3177e016118d`; the support base is PR #507, `e726deb96dc74bacd40c2fe0ef640c6db57903f5`. R2 was opened as draft PR #514 from `dfc4c1088c240aea536955caaeaf4740e5aadd9c`; that initial commit alone does not register the subsequent probe source.

The question is whether one strictly bounded native training update on the existing 16,011,776-parameter model completes on this host, and what the externally measured process peak memory and elapsed time are. This is a genuine single training update for resource measurement, not a long continuation run or a quality experiment. There is no held-out corpus, model selection, promotion, or Android/Fold7 feasibility claim.

## Frozen inputs

| Input | SHA-256 |
|---|---|
| `continuum-lm1-16011776.lm1` | `93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312` |
| `lm1-tokenizer.lmbp` | `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f` |

The harness hashes both regular, non-symlink files before parsing and checks the fixed identities before creating a trainer. Native `LmCheckpoint.decode` verifies the checkpoint body digest, tokenizer identity, exact layout and finite weights. Real mode additionally requires minGRU, dim512, five layers, 57 tensors and exactly 16,011,776 parameters. Full input hashes are measured again in a `finally` block when the JVM can complete normally. On external timeout, kill or fatal process failure, Root must separately re-hash both input files and record that the in-process final checks did not complete.

## Frozen update

| Setting | Value |
|---|---|
| Trainer | Existing unmodified `LmTrainer` |
| Optimizer | Fresh native `LmAdamW`; no optimizer checkpoint exists or is restored |
| Batch | Exactly one sequence |
| Sequence | `[tokenizer.bos, 112, 114, 111, 98, 101, 33, tokenizer.eos]` |
| Meaning | BOS, six raw byte tokens for ASCII `probe!`, EOS; no BPE text encoding |
| Sequence length / targets | 8 / 7 |
| Updates | Exactly one call to `trainer.step(listOf(sequence))` |
| Learning rate | `0.0001f` |
| Warmup / totalSteps | `1 / 1` |
| Effective first-update LR | Native `rate(0) = learningRate * (0 + 1) / 1 = 0.0001f` |
| Weight decay | `0.01f`; native decay mask applies only to weight matrices |
| Global clipping threshold | `1.0f` |
| Native beta1 / beta2 / epsilon | `0.9f / 0.98f / 1e-8f`, audited source defaults |
| Training workers | Exactly one |

The checkpoint's weights are loaded as-is. The optimizer constructor creates fresh zero moment buffers. Parameter initialization and optimizer reset are not confused: only the optimizer is new. No extra gradient pass, custom optimizer, reflection, or production-source modification is used.

## Measurement and observable limits

The TSV stdout reports input hashes before/after, complete model configuration, counts, sequence, optimizer configuration, `trainer.steps` before/after, measured `trainer.rate(0)`, native step duration, pre-update native loss, independent inference NLL before/after, finite weights, a streaming in-memory weight fingerprint, exact bitwise and numeric changed counts, maximum absolute change and L2 change. The comparison streams the original checkpoint; it does not allocate a second full parameter snapshot or save trained weights. The fingerprint is SHA-256 over native layout order, modified-UTF parameter name, big-endian shape integers and raw IEEE754 float bits. It is distinct from the whole-checkpoint SHA.

The independent pre/post NLL uses the same seven synthetic targets. The native step's returned loss is its pre-update mean NLL; it must agree with the separate pre-update inference mean to relative tolerance `1e-5 * max(1, abs(preNLL))`. All three loss observations must be finite. The post-update loss need not improve: no quality conclusion is licensed by this sequence.

**Direct gradient finiteness and norm remain UNMEASURED.** `LmTrainer` keeps its buffers private and discards the global norm returned by `LmAdamW.step`. Private optimizer moments are likewise unmeasured. Finite losses, finite output weights and a successful native update are reported exactly as observed; they are not relabeled as a direct gradient inspection. This restriction follows Root's requirement to keep the production trainer unchanged and avoid reflection. It leaves a specific validation gap after the resource measurement.

Peak memory is supplied only by Root's external process wrapper (`VmHWM`/maximum RSS). It includes JVM startup, native checkpoint decoding and transient copies, optimizer moments, gradient buffers, tape, inference checks and observer overhead. It is not an isolated optimizer peak or an Android measurement. JVM heap ceiling is not a total-RSS guarantee. No forced garbage collection is used.

## Hard execution budget

Run one fresh JVM with `-Xmx3g -XX:ActiveProcessorCount=1`; one native training worker is fixed in source. Real mode refuses a heap ceiling above 3 GiB or a reported active processor count other than one. Root's wrapper must enforce at most **120 seconds total process lifetime**, including any termination grace period; for example `timeout --signal=TERM --kill-after=5s 115s`. Record timeout/exit status and external memory without treating a partial TSV as success. No automatic real-model retry or changed-budget rerun is authorized by this protocol.

Invocation (classpath supplied by Root's audited local build):

```text
java -Xmx3g -XX:ActiveProcessorCount=1 -cp CLASSPATH at.heinz.deviceai.learning.lm.LmTrainingResourceProbe --real16m ABSOLUTE_CHECKPOINT_PATH ABSOLUTE_TOKENIZER_PATH
```

Root records the exact registration commit, compiler/runtime versions, JVM flags, complete argv and hashes of the compiled production inputs and probe. The one-step success criterion is exit code zero, trainer steps 0→1, positive frozen LR, finite losses/weights, positive numeric changed count, different weight fingerprint, unchanged checkpoint/tokenizer files, and completion within the external budget. Gradient and optimizer-moment claims stay unmeasured.

## Tiny validation before real execution

`--self-test NEW_EVIDENCE_DIRECTORY` creates only a new byte-tokenizer fixture and a deterministic minGRU with dim8, one layer, context8, default MLP32 and seed20261104. It loads this tiny checkpoint afresh for each of two one-update runs, checks identical resulting fingerprints/losses/change statistics, and verifies that a wrong expected checkpoint hash is rejected before trainer construction. This is a test of the harness and native API integration, not evidence about 16M resource use. A previously existing fixture directory is rejected.

No real trained checkpoint is written. No main merge, deployment, paid API, GitHub Actions, APK build/install/signing, active-model replacement or external publication follows from this protocol.
