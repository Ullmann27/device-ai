# LM1 semantic candidates: prospective proposal

Status: PREPARED / NOT_RUN / ROOT_GATE_REQUIRED. EXP-05 / EXP-12.
Architecture remains original MINGRU, 132,160 parameters, 512-token vocabulary,
64 hidden units, two layers, context 256. Original checkpoint and tokenizer
are verified by immutable SHA-256; all baseline and historical files stay
unchanged. No holdout contents or labels are available to training.

## Shared bounded schedule

Exactly A and B, each fresh from original weights and fresh AdamW moments.
Maximum 128 updates each, seed 20261111, one CPU worker, 768 MiB Java heap.
Four training examples per update, one per class in the 0/1/2/3 order.
Use the unchanged source-pinned native optimizer with its existing isolated
continuity patch: rate 0.0001, warmup four updates, cosine decay to 10%,
weight decay 0.01, clip norm 1. No new framework or architecture.

Each instruction uses BOS + encode("Auftrag: " + prompt + "\n"), then one
label token. Supervise mean label cross entropy plus 0.25 times mean EOS
cross entropy. Prompt targets stay masked. Separating the EOS objective
is a prospective response to the observed early stopping failure; this is
not a demonstrated causal repair. Both candidates share exactly the same
answer schedule and objective. All four classes are trained, unlike the
earlier two-class pilot.

## Candidate A

Only the balanced instruction objective. No retention examples enter updates.

## Candidate B: targeted preservation

The same instruction objective, plus:

- 0.5 times mean label cross entropy on separate training-only open/search
  anchors for which the unchanged original actually picks the correct label
  under the published four-choice diagnostic. Select a fixed balanced subset
  before training. This protects observed weak label-ranking signals; the
  old aggregate 32/64 score equals balanced chance and is not robust semantics.
- 1.0 times mean full-vocabulary KL(original || candidate) at positions of
  original greedy rollouts from separate training-only neutral text prefixes.
  Use actual original trajectories reaching at least 24 tokens, up to 32.
  Exclude prefix positions and do not add artificial EOS targets to long
  trajectories. Cache the frozen original distributions, without aliasing
  the trainable parameters, and check teacher/cache identities afterward.

This differs from the failed teacher experiment: its sixteen teacher-forced
statements did not protect any intent targets or actual generation trajectory.
New anchors and text prefixes are distinct from all retained evaluation cases.
Anchor filtering and trajectory qualification use only original inference;
no candidate-dependent selection, development/holdout tuning or retry.
Insufficient valid anchors/trajectories makes B NOT_CONFIGURED; no replacement
experiment. Prefixes and retention choices must be frozen before either run.

## Checkpoints and early stop

Record deterministic sampled IDs and losses at every update. Save real LM1C
weights and complete LM1T state (weights, optimizer moments, step, schedule,
seed and source/data identity). At step 64, save/load and reserialize the
full state before continuing the same candidate; require exact identity.
This is one resumed 128-step run, not additional optimization steps.

Every 32 updates inspect only fixed training monitoring cases, never holdout.
Stop and save the candidate if nonfinite values appear, if at least 7/8
monitoring instructions stop immediately with EOS, or if all original long
training continuation probes shrink below four generated tokens. Do not
change settings or restart after stopping. Native process limit 600 seconds;
external entire-run limit 660 seconds. Serialize whatever valid checkpoint
has been completed; failed and aborted results are retained.

A and B have unequal compute because B includes preservation. Record actual
wall time, CPU time and peak RSS where available. No GPU, paid runner,
cloud-model calls or claimed credit totals. Only the independent evaluator
may judge generalization, statistical uncertainty and promotion gates after
the final weight identities are fixed. No candidate is auto-promoted.
