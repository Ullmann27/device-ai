# Direct LM1 weight-identity experiment

Status at authoring: NOT_RUN. Source baseline: PR #530,
`4b8fa96effd5a390bd5fd83ed84b81ac850b1d5b`.

## Defect and hypothesis

Historical `weights_only_restart_diverges` compares complete training-state
identities at different step counters. This is insufficient to demonstrate
weight divergence. The moment-reset check likewise compares complete state.
This new experiment measures every parameter bit directly, independent of step,
optimizer state, identity metadata, or checkpoint naming.

Two negative controls preserve all weights while changing only identity seed or
optimizer step. The historical state-inequality criterion must accept each as
"different" (a demonstrated false positive); the direct weight criterion must
reject both as weight changes. No source or historical result is rewritten.

## Fixed execution

Use unchanged `LmTrainingFixture`, the pinned native sources, the existing
`training-continuity.patch`, and `LmTrainingState`. Compile this new harness
separately with the provisioned Kotlin compiler and Java 21. Publish source,
source hashes, compiler identity, executable hash and exact command before the
native run. A compile is not an experiment run.

Exactly one native invocation; 180-second timeout; `-Xmx512m` and one reported
processor. Fresh output directory; never overwrite earlier evidence. Any
failure remains recorded. No automatic changed-parameter retry.

The deterministic 2,632-parameter fixture performs exactly 80 optimizer updates:

| Trajectory | Actual updates | Final step |
|---|---:|---:|
| Uninterrupted | 24 | 24 |
| Shared prefix | 8 | 8 |
| Complete restore | 16 | 24 |
| Weights-only restore | 16 | 16 |
| Correct step, reset moments | 16 | 24 |

Metadata and step-only controls perform zero updates. No inference, model
selection, 16M update, external model download, or held-out quality evaluation.

## Predeclared criteria

- Both negative controls: state hashes differ, all weight bits identical.
- Initial to step 24: at least one numerically changed parameter.
- Prefix save/load: every weight bit identical; step eight and nonzero moments.
- Complete restore: every final weight bit and complete-state identity match.
- Both incomplete restores: at least one numerically changed final parameter
  relative to uninterrupted training.
- Prefix file unchanged; actual optimizer updates equal 80.

Record full SHA-256 of tensor-only snapshots, exact changed-bit and changed-value
counts, maximum absolute numerical delta, all raw output and process status.
The tensor digest encodes names, shapes and raw Float32 bits. A separate equality
loop supplies counts; checkpoint metadata cannot alone satisfy divergence.

## Interpretation and rollback

This establishes software-level training continuity and whether weights really
change. It is not evidence of improved language understanding. The earlier
model-quality failures remain unchanged; consumed holdout data are unused.
All additions stay in this directory, with no application/runtime activation.
Rollback: omit the new research files. Preserve historical sources and evidence.
