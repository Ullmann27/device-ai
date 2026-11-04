# LM1 training recovery — 2026-11-04

Status: WIP / EXPERIMENTS_NOT_RUN. This is a new research round authorized by the owner after the completed G1 gate repair and 6–10M experiments.

## Source of truth

- Parent: PR #492, commit `30cd3a89810ed14fa84eca95236d3177e016118d`.
- Own branch: `research/continuum-lm1-training-recovery-20261104`.
- The earlier helper PR #497 is historical. Use the strengthened integrated gates in #492; do not replace them with the earlier helper patch.
- Keep all new research code under `experimental/lm1-training-recovery-20261104/` and its evidence under `docs/evidence/lm1-training-recovery-20261104/`.

## Verified starting point

The parent reports successful initial 6,562,304 and 9,712,128 parameter checkpoints. Its four 16-step continuation runs complete optimization but produce immediate EOS at the capacity prompt and are rejected. Weight changes alone do not establish learning. The parent production runtime and all historical inputs/evidence remain unchanged.

## Assigned independent work

1. Training helper: inspect the EOS mechanism and implement an isolated diagnostic harness around the existing LM1 kernels/trainer.
2. Architecture helper: primary-source research on function-preserving growth, width-dependent optimization and small-model curricula.
3. Validation helper: independent new intent-task data splits and prospectively specified acceptance criteria.
4. Coordinator: bind source/input identities, build locally, run bounded comparisons, review all outcomes and publish raw evidence.

## Planned research, not results

- Reproduce the unchanged small and width-8 continuation on the same historical TRAIN token schedule; record EOS probability/margin and fixed development loss at steps 0, 1, 4 and 16.
- Test two isolated alternatives: a smaller final-norm update and a smaller global learning rate. These are hypotheses, not presumed repairs and not a full muP implementation.
- Independently test a narrow German intent-learning task using self-authored train/development/test templates. Compare small and widened models before and after training. Use free greedy output, including the expected EOS, rather than only teacher-forced scores.
- Bind the new test split before training. Select candidates on development data only; do not tune against old disclosed holdouts or the new final test results.

Every actual experiment needs its final protocol, source hashes, fixed budget and input hashes committed before it starts. Empty/failed results are retained. No claims of general intelligence, Android performance or successful app integration follow from a task-specific host result.

## Boundaries and rollback

No foreign pretrained weights, paid AI API, GitHub Actions, production training, model promotion, release or change to active app selection. Run only on isolated copies of the owner's LM1 checkpoint. Keep original checkpoints immutable. Each failed gate stops the affected run. Discard any unsuccessful candidate and keep the existing model; existing evidence must not be overwritten.

Current execution status: no new training run has started. Local build tools are being verified; source and result checkpoints will follow here before experiments.
