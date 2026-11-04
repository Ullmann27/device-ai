# Bot 05 — model experiment evidence and interrupted resume

Read-only audit of weight workstream #492 on 2026-11-04. Agent-observed head:
`b1acc3d16ca7435603f61a48ab81b42177407fe2`. The workstream continued moving;
the exact inspected runner bytes remained identified by SHA-256
`6b14f23ef9b595008099b16d81f1121783fea0bb67e7f3d942e44eed6405e425`.
File: `experimental/weight-space-20261104/run_experiment.py`.

## Finding and independent reproduction

`write_new` writes JSON directly into the final exclusive output path. An
interruption during serialization leaves a visible incomplete file. The ladder,
continuation and final entry points skip work when their output path exists,
without first validating it.

Bot 05 established the source control flow. The coordinator then independently
reproduced it against those exact runner bytes, using only a synthetic partial
writer and three temporary malformed phase files. The `Experiment` constructor,
model calls and owner input reads were never invoked.

```sh
python3 tools/support_assist_20261104/weight_phase_resume_probe.py \
  /path/to/experimental/weight-space-20261104/run_experiment.py
```

Actual result: exit 1, incomplete final file visible after the synthetic writer
interruption; all three phase methods silently return for malformed existing
JSON. Raw evidence: `bot05-resume-probe.json`. At reproduction the owner's local
head was `88d7b05072bdec6250990efaa9914a1e6744c29b`, with the same runner hash.
This does not establish that a real training run has already been damaged.

## Handoff to the weight owner

Publish a fully serialized result atomically while preserving exclusive-create
semantics; validate existing phase outputs and expected dependencies before
accepting them as completed. Do not overwrite prior evidence silently. The
weight workstream owns the production repair and its full experiment checks;
the support team has not modified its runner or weight files.

The audit also observed checkpoint/tokenizer/dataset hash binding and failed
continuations excluded from selection. Capacity numbers are labelled host
measurements; Fold7 quality and speed remain NOT_RUN. No model, private corpus,
training or capacity experiment was run by this support check.
