# LM1 intent study v2: longer fresh training

Registered prospectively on 2026-11-04. Scope: `RESEARCH_SHADOW`.

## Why this round exists

The preceding registered round used source commit
`def5acf9cbba82ee51a53cc06fecc52e9cd0324d` and 128 optimizer updates.
Its selected small model reached 8/24 exact development cases, and its
selected 6,562,304-parameter model reached 10/24. Both learned single-digit
answers with EOS. The widened model did not reach the predeclared 12/24
development threshold, so `NO_DEVELOPMENT_SIGNAL` was recorded. No final test
was evaluated and no selection was frozen for testing. Both selected models
failed the separate original 32-token generation capacity benchmark.

This round tests a longer fresh run with a stretched cosine schedule. It does
not continue either rejected trained checkpoint. A single final minibatch loss
does not establish undertraining; an alternative explanation is a gap between
the training templates and the development language. Both possibilities remain
open. We do not change the architecture, suffix, data, answer objective,
learning-rate peak, seed, or interpretation of EOS in this round.

## Fixed comparisons and data

All task definitions, four classes, split boundaries, free greedy scoring,
per-class thresholds, test restrictions, initial capacity gate, and absence of
promotion from `../INTENT_PROTOCOL.md` remain in force, except for the explicit
training budget and snapshot changes below. The same 128 training, 24 development
and 48 previously unevaluated final-test cases are used. Their immutable files
remain under `../intent-data/`; hashes appear in the new registration. Test
templates are public in the original generator: this is prospective separation
of evaluation, not cryptographic secrecy.

The four arms remain S0 (original small model), W0 (fresh width-8 transformation),
S1 (fresh small training) and W1 (fresh widened training). Original weights and
tokenizer, widening seed/noise, parameters and parity requirements are unchanged.
S0 and W0 must independently pass the unchanged capacity benchmark before any
training update. No prior `lastPassed`, selected checkpoint or failed run state
is imported. Every output directory must be new. There is no implicit resume.

| Setting | Fixed value |
|---|---:|
| Original small parameters | 132,160 |
| Widened parameters | 6,562,304 |
| Optimizer updates per trained arm | 512 |
| Batch size | 4 complete examples |
| Full dataset passes | 16 |
| Supervised answer/EOS targets per arm | 4,096 |
| Peak learning rate | 0.0001 |
| Optimizer | Existing AdamW, decay 0.01 |
| Warmup / cosine floor | 4 steps / 0.1 |
| Gradient clipping | Global norm 1 |
| Training seed | 20261104 |
| Gradient workers | 2 |
| Development snapshots | 0, 128, 256, 512 |
| Trained selection candidates | 128, 256, 512 only |
| Parent JVM heap | 3 GiB maximum |
| Hard process deadline | 900 seconds per training arm |
| Internal training-loop deadline | 840 seconds |
| Each fresh capacity child | 1 GiB heap, 120 seconds |

Cosine decay now extends over 512 updates. Therefore even the first 128 updates
do not have the same learning-rate sequence as round v1. Any change relative to
v1 concerns the combined longer run and stretched schedule; it cannot be
attributed solely to the extra updates. S1 and W1 within this round receive
identical complete examples, token schedules, update counts and rate schedule.
Their independently selected checkpoints may have different step counts, which
must be reported and precludes isolating model size as the sole cause.

## Unchanged selection and success criteria

Select each trained arm by development exact-answer count descending, then
answer-token NLL ascending, then fewer steps. Selection requires complete,
hash-bound training evidence. W1 must reach at least 12/24 development successes
and at least four more than its initial W0 before any test is evaluated.
Otherwise record `NO_DEVELOPMENT_SIGNAL` and leave the final test unevaluated.

Before the single test evaluation, bind all four model hashes, source commit,
registration, token schedule and test-file hash in a new freeze receipt and
publish it. Reconstruct and compare that complete receipt before testing. Verify
the actual JVM-reported checkpoint and test identities as well as the files
after each process. There is no selection or tuning using final test outcomes.

The limited task-learning criterion remains all of:

- W1 at least 36/48 freely generated exact digit-plus-EOS answers.
- W1 at least 8/12 correct in every class.
- W1 at least eight more correct than both S0 and W0.
- Complete identity, split, training and evaluation evidence.

A selected widened-model advantage is a separate observation requiring at least
four more correct test cases than S1. Test cases share template families, so 48
cases are not 48 independent draws and no binomial significance claim is made.
Neither criterion establishes general intelligence or open-ended tool competence.

The unchanged original capacity benchmark is repeated after training. A failure
is reported alongside any task result and prevents a general-model suitability
or promotion claim. Exact single-digit completion is not substituted for its
32-token requirement. All outcomes, including a second failure, are retained.
As descriptive diagnostics, epoch means may be calculated from the already
logged 32 minibatch losses per epoch; no extra inference or final-test access is
needed, and this does not change the selection rule.

## Implementation, source binding and rollback

The long harness is a separate copy of the already checked answer-masked harness.
Its controlled differences are its class/experiment identity, update count,
snapshot list and internal deadline. The outer controller retains its checked
selection reconstruction and byte bindings, uses the long harness and deadline,
and exposes only intent/freeze/test phases. A new compiled JAR and registration
bind all executable sources. Earlier sources, registration, JAR and evidence
remain immutable.

All computation uses local CPU and the owner's own LM1 weights. No foreign
pretrained model, paid AI API, production training, release, Android installation
or active-model change is involved. To roll back, do not adopt the research
checkpoints and keep the existing original model. No application rollback is
required because no application selection or runtime file is changed.
