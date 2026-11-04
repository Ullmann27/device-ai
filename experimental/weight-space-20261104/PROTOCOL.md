# Weight-space and capacity experiment

Status at registration: RESEARCH_SHADOW; no quality outcome known.

## Purpose

The owner requested direct examination and improvement of existing model weights,
then explicitly requested a language core with millions of parameters. Reuse the
existing LM1 implementation and its trained minGRU checkpoint. The small SWB1
intent/similarity encoder is not a language-model replacement target: its live
callers and private snapshot remain unchanged.

## Fixed source and data

- Runtime base: PR #478, `d8fd7f7cc5aa721b2c4a975c527c5123e216a944`.
- Original LM1 checkpoint: `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`.
- Tokenizer: `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`.
- Both came from the existing data branch at `d2304a7658e1256b160c8012e1bac28268ff64a5`.
- The baseline has vocab 512, dim 64, two minGRU layers, MLP 256 and 132,160 parameters.
- Continuation data must come only from the original TRAIN document split at
  `1d92db03b87558c0b330dc584fd1f4c025a93c82`, cleaned by existing `LmCorpus`.
  Preserve the original path-based split; exclude old VALIDATION and TEST documents.
  Limit continuation to 16 steps, batch 4, at most 63 content tokens per example.
- Development data: the separately authored `development.txt` in this directory.
- A separate agent authors 32 fresh generic German holdout paragraphs. Its text is
  withheld from the selecting agent; seal the hash before candidate evaluation.
  These are synthetic language-probability probes, not an external capability benchmark.

## Experiment A: no-gradient edit

Source verification completed before candidate evaluation: the original Qwen study
edits selected MLP output columns in layers 15 and 19. Its rules expressly prohibit
final-normalization edits and constant whole-tensor scaling. Therefore the following
LM1 normalization sweep is a separate calibration diagnostic, **not a replication of
that Qwen experiment**. LM1 has no demonstrated corresponding loop direction. Do not
claim the Qwen mechanism or its benchmark gains for this experiment.

Primary references:
- https://rikiyat.github.io/blog/weight-space-programming/
- https://github.com/RikiyaT/lm_understand-logs/blob/main/exp8_math/README.md
- https://research.google/pubs/net2net-accelerating-learning-via-knowledge-transfer/

Compare the unchanged model with independent scalar edits to each of `l0.n1`,
`l1.n1` and `nf`, using factors 0.75 and 1.25. Only one tensor changes per trial.
Use development bits per UTF-8 byte (BPB; lower is better) for selection. The
identity candidate is eligible. A candidate must have finite probabilities,
unchanged layout/tokenizer, an intact original, and lower development BPB before
it can become the selected experimental edit. Do not infer instruction-following
or mathematical ability from BPB. Record every trial, including regressions.

## Experiment B: learned-core expansion

Use minGRU only. Expand dim and MLP by an integer factor using a function-preserving
replication of input/output channels. Dense weights divide by input replication;
the final normalization gain divides by the factor because output embeddings are
tied. Optional deterministic zero-sum perturbations across replicated input
channels break symmetry while preserving the initial function within measured
floating-point tolerance.

- Factor 4: 1,708,288 parameters.
- Factor 8: 6,562,304 parameters, 26,249,216 parameter bytes (FP32), excluding runtime state.
- Trial target: factor 8 if resource and parity checks pass. No larger shape is
  needed for this bounded experiment.
- Fresh forward/generation and checkpoint round-trip tests must show that expansion
  preserves the learned baseline before any additional updates.
- Run the same fixed 16-step data schedule for a small-model control and the expanded
  model. Learning rate 0.0001, AdamW weight decay 0.01, warmup 2, gradient clip 1,
  two workers. These are engineering choices, not owner-specified hyperparameters.
- Record parameter changes and replica divergence after training. Expanded copies
  before adaptation add capacity, not new knowledge; report this explicitly.

## Frozen selection and final evaluation

Candidate selection uses development BPB only and is recorded with every checkpoint
hash before opening the holdout. Once frozen, evaluate baseline, selected no-gradient
edit, continued small control, expanded initial model and continued expanded model
once on the same holdout. Do not tune again on it. A new experiment needs a new
holdout. Prefer the smaller model unless the larger one demonstrates an actual
benefit; every result remains an offline research result.

Report BPB, matched training tokens, parameter count, changed weights, source and
tokenizer hashes, wall times, integrity tests, and all failures. Show a fixed set of
generation probes with strict literal scoring separately from BPB. No app improvement,
Android speed or general reasoning success is inferred from a host run.

## Boundaries

No main merge, active model replacement, paid compute/API, GitHub Actions, release,
installation, S01 change, new shell authority or private data in Git. Output candidates
use the existing content-addressed LM1 store. Original SWB1/LM1 snapshots are read-only.
Rollback consists of retaining the original snapshot and rejecting the candidate.
