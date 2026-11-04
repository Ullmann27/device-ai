# Training resource helper handoff

## Frozen deliverable

The Kotlin source is frozen at SHA-256 `7638fcbe04f7ae53a0ab086ca716002218955d27b7dc2abb7d48d50fa79e31bf`. Root reviewed this version and owns its registration and the single bounded actual-16M execution. This helper has not created an optimizer for, or trained, the actual 16M checkpoint.

All files below are under:

```text
/workspace/scratch/319f3e4563d9/agents-r2/training/experimental/weight-space-20261104/support-round2/
```

| File | Purpose |
|---|---|
| `LmTrainingResourceProbe.kt` | Frozen native probe and tiny self-test |
| `TRAINING_RESOURCE_PROTOCOL.md` | Original helper protocol; Root will clarify the description of the actual single training step before registration |
| `TRAINING_PROBE_TINY_RECEIPT.json` | Exact compiler/test argv, runtime, input source hashes and artifact identities |
| `tiny-validation-v1.tsv` | Complete two-run tiny validation output |
| `build/compile.log` | Empty successful compiler log |
| `build/lm-training-resource-probe.jar` | Locally compiled test JAR; Root should build from registered source for actual execution |
| `tiny-validation-v1/tiny-input.lm1` | Tiny original checkpoint fixture, 2,904 parameters |
| `tiny-validation-v1/tiny-tokenizer.lmbp` | Byte-tokenizer fixture |

The receipt's protocol hash identifies the helper's original file, not a later Root editorial revision. The receipt SHA-256 is `fa39259f8285610a3401184d90fdfb80d5b2f03fc7c583854bbc790186244716`.

## Exact entry points

Main class:

```text
at.heinz.deviceai.learning.lm.LmTrainingResourceProbe
```

Real mode arguments:

```text
--real16m ABSOLUTE_CHECKPOINT_PATH ABSOLUTE_TOKENIZER_PATH
```

Tiny mode arguments:

```text
--self-test NEW_EVIDENCE_DIRECTORY
```

Real mode requires `-Xmx3g` or smaller and `-XX:ActiveProcessorCount=1`; the external wrapper enforces the complete 120-second process ceiling. Source fixes one native worker, one batch, one eight-token sequence, seven targets and exactly one `LmTrainer.step` call. LR is `1e-4f`, warmup1, totalSteps1, weightDecay `.01f`, clip1. Native AdamW defaults are beta1 `.9f`, beta2 `.98f`, epsilon `1e-8f`. The sequence uses explicit raw byte IDs for `probe!`, with BOS/EOS, rather than BPE text encoding.

## Tiny result and bounded review

Compilation succeeded with Kotlin 2.0.20, JVM target17 and OpenJDK21.0.13. The four production sources were compared byte-for-byte to commit `30cd3a89810ed14fa84eca95236d3177e016118d` and all matched.

The tiny self-test exited zero. Each of two independent fresh loads/trainers observed steps0→1 and rate`1e-4`, finite losses and weights, 2,904 numerically changed parameters, and unchanged checkpoint/tokenizer input hashes. Both runs produced exactly the same output fingerprint, loss values and delta statistics. Wrong expected checkpoint SHA was rejected before trainer construction.

| Tiny observation | Value |
|---|---|
| Pre-update inference NLL | `5.592717195645469` nats/target |
| Native step's pre-update NLL | `5.592717170715332` nats/target |
| Post-update inference NLL | `5.590669211302803` nats/target |
| Updated parameter fingerprint | `788e599ed2150a036eb75030737e907b2e89bd1c59c2fa8a192f9dc1c033fb6b` |

This numerical tiny observation does not establish model quality or actual-16M memory consumption.

The code review confirmed that real-mode SHA and model-size guards precede trainer construction; the native trainer is unchanged; exactly one native update is invoked; no reflection is used; the streamed changed-weight comparison avoids a second full parameter snapshot; and the real path contains no file-writing operation. Hashes are rechecked in `finally` on normal JVM execution. Root must re-hash inputs externally after timeout or process termination, because the in-process `finally` block cannot be guaranteed in those cases.

The final real-mode success marker is `status<TAB>PASS_ONE_NATIVE_UPDATE`, emitted only after the input-hash `finally` block returns successfully. The earlier `status<TAB>RUNNING` row is a progress marker. Exit status and the final marker must be checked together.

Direct gradient finiteness/norm and private moment finiteness remain **UNMEASURED**. Finite losses, finite updated weights and a successful single native update must not be presented as direct gradient inspection. External peak RSS includes loader, optimizer, gradients, tape, inference checks and observer overhead; it is not an isolated optimizer or Android measurement. No trained checkpoint is saved and no quality/promotion claim is made.
