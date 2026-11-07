# Independent 16M checkpoint preflight

Status: **RESEARCH_SHADOW — checkpoint structure PASS; training memory UNMEASURED.**
This contribution adds only an offline Python reader, synthetic negative controls,
and the result from the actual supplied checkpoint. It does not execute a model,
train, modify a checkpoint, change a dataset split, or promote a runtime candidate.
Integration and publication belong to the coordinating agent in draft PR #507.

## Actual artifact result

The independent reader successfully consumed all weights of the historical
16,011,776-parameter expansion. Its source format is pinned to
`30cd3a89810ed14fa84eca95236d3177e016118d`; the receipt verifies that the three Kotlin
format sources still match that commit byte for byte.

| Check | Observed result |
| --- | --- |
| Format | LM1C version 1; big-endian FP32 tensors |
| Architecture | MINGRU, vocab 512, dim 512, layers 5, heads 4, context 256, MLP 2048 |
| Full ordered layout | 57 tensors; every name and shape matched |
| Actually read float values | 16,011,776; all finite |
| Raw parameter bytes | 64,047,104 |
| Entire checkpoint bytes | 64,048,511 |
| Fixed checkpoint limit | 67,108,864 bytes (64 MiB) |
| Checkpoint headroom | 3,060,353 bytes |
| Tokenizer | LMBP; 254 valid backward-referencing merges; vocab 512; 2,040 bytes |
| Internal body SHA-256 | `2eb1dece023077dc656364db3685b63aff83b47078109e06d8d78da10cdc4dd7` |
| Entire checkpoint SHA-256 | `93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312` |
| Tokenizer SHA-256 | `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f` |
| Before/after input digests | Identical |

Evidence: [complete parsed result](weight16m-preflight-evidence/real16m.json),
[source and execution receipt](weight16m-preflight-evidence/receipt.json),
[test log](weight16m-preflight-evidence/tests.txt).

For MINGRU the parameter formula is
`vocab*dim + layers*(4*dim*dim + 2*dim*mlp + 4*dim + mlp) + dim`.
The output projection shares the token embedding, and MINGRU has no positional
embedding. Increasing this exact width to six layers would require 19,161,600
parameters, or 76,646,400 raw FP32 bytes before checkpoint overhead. That exceeds
the unchanged 64 MiB checkpoint limit. This latter number is an analytical
boundary check, not an available or trained six-layer checkpoint.

## What can honestly be continued

The supplied bundle manifest identifies this checkpoint as a **function-preserving
expansion** and explicitly excludes rejected continuation weights. The manifest
is preserved as [source-bundle-manifest.json](weight16m-preflight-evidence/source-bundle-manifest.json);
its SHA-256 is recorded in the execution receipt. The parsed metadata contains
width factor 8, three added layers, and zero-sum noise 0.001. The inherited
`epoch=8` field does not prove eight epochs of training at 16M parameters.

The actual weights and matching tokenizer are structurally compatible with the
existing LM1 reader. A subsequent authorized experiment can start from those
weights. **An exact optimizer resume is unavailable:** LM1C version 1 saves weights
and metadata, but no Adam moment arrays, optimizer step, or complete training
execution state. `LmTrainer` creates a fresh `LmAdamW`; its step counter begins at
zero. Continuing these weights therefore starts a new optimizer trajectory and
requires an explicitly recorded training setup.

**Training memory remains UNMEASURED.** The 768 MiB contract and the native result
below concern the inference capacity benchmark. They do not show that an Adam/Tape
training step fits within 768 MiB. Weight data plus Adam's two moment arrays alone
account for at least 192,141,312 raw bytes; gradients, the differentiation tape,
activations, worker copies and JVM overhead add further memory. This is only a
lower bound, not a training peak estimate. The existing experiment driver uses
`trainingHeap=3g`. No training-memory PASS or successful quality improvement is
inferred from this preflight.

Sources at the pinned format commit:
[LmCheckpointStore.kt](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/app/src/main/java/at/heinz/deviceai/learning/lm/LmCheckpointStore.kt),
[LmModel.kt](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt),
[LmBpeTokenizer.kt](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/app/src/main/java/at/heinz/deviceai/learning/lm/LmBpeTokenizer.kt),
and [existing driver](run_experiment.py).

## Separate native inference observation

The coordinating agent executed the unchanged Kotlin capacity benchmark on the
same checkpoint, with a newly compiled Kotlin 2.0.20 / Java 21.0.13 host artifact.
The independently inspected raw logs report all three complete 1/32-token pairs,
maximum one-token call 624.662315 ms, maximum 32-token call 889.600476 ms, and maximum
observed VmHWM 289,180 KiB (282.40234375 MiB). The fixed thresholds remain 1,500 ms,
5,000 ms and 786,432 KiB. The final VmHWM record is lower; the maximum of all samples
is used. This observation is inference-only, host-only, and is not a reproduction
of the historical pinned training JAR or Android acceptance.

Sources: [native raw log](support-319f3e-evidence/real16m/capacity.tsv) and
[native receipt](support-319f3e-evidence/real16m/receipt.json). Their digests are also
recorded in this preflight's receipt. The Python preflight deliberately reports
`inferenceCapacity=NOT_MEASURED_BY_PREFLIGHT`; it does not substitute these separate
observations for a new measurement or a training-memory result.

## Reproduce without a JVM or external packages

Run from the repository root after supplying the actual model and tokenizer.
Adjust the two input paths to the read-only bundle on the new host:

```sh
python3 experimental/weight-space-20261104/weight16m_preflight.py \
  --checkpoint /path/to/model/continuum-lm1-16011776.lm1 \
  --tokenizer /path/to/tokenizer/lm1-tokenizer.lmbp \
  --expect-parameters 16011776 \
  --expect-checkpoint-sha256 93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312 \
  --expect-tokenizer-sha256 94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f

python3 -m unittest discover \
  -s experimental/weight-space-20261104/tests \
  -p test_weight16m_preflight.py -v
```

The CLI prints JSON and exits 0 for structural PASS, 2 for FAIL, and 3 for
UNAVAILABLE input. Its default target is 16,011,776 parameters. A missing bundle
does not generate synthetic replacement weights or imply a successful check.
It reads FP32 values in 64 KiB blocks and has no NumPy, JVM, network or training
dependency. There is no writable checkpoint operation.

Validation completed with Python 3.12.14: **14/14 tests passed**, covering tiny
synthetic MINGRU/Transformer fixtures, the architecture count, the next-depth
checkpoint boundary, corrupted checksums, self-consistently rehashed NaN and
infinity, wrong tensor name/shape, tokenizer binding/forward references, truncated
and trailing data, invalid headers, wrong expected identity, oversize files,
Java modified UTF encoding and missing-input UNAVAILABLE. The synthetic fixtures
are separate from the real-checkpoint evidence and prove no model quality.

## Handoff boundary

These files are new, isolated experiment support files. Existing runtime, app,
driver, inference benchmark, datasets and gates remain unchanged. The next
training work belongs to the separately coordinated PR #508; it should pin this
input identity and independently measure training resources and quality. No
consumed holdout was used for adaptation here. Rollback consists of omitting this
standalone preflight contribution; there is no runtime dependency on it.
