# Candidate EXP-06: frozen-teacher rehearsal

Status: PROPOSAL / NOT_RUN / not registered. One candidate, no tuning sweep.
This proposal was authored after seeing EXP-05 answer-arm diagnostics and
before seeing replay-arm model results. Existing EXP-05 runs remain unchanged.

## Mechanism established by source

`LmModel.logits` gathers token embeddings from `tok` and also uses the same
matrix as the final transposed output projection. There is no separate
language-model head. Every answer sample supervises one label and EOS, and
full-model training changes both shared embeddings and the internal hidden
mapping. This permits an EOS preference learned after short labels to disturb
other contexts; the observed collapse is consistent with that mechanism, but
this source inspection does not prove which parameters caused it.

The existing answer arm increases diagnostic exact match while all four
continuation probes stop early. The retained-text BPB increases only 3.13%,
showing that aggregate teacher-forced loss can miss a severe greedy-decoding
failure. Baseline already stops early on two probes; no experiment can claim
full language capacity merely by recovering the other two.

## Alternatives and choice

| Candidate | Small implementation | Confound or limit |
|---|---|---|
| Existing 0.1 text rehearsal | Already frozen and executing separately | Hard targets preserve only observed next tokens, not original distributions |
| Head-only freezing | Not directly available | `tok` is both input and output; untying creates an architectural/checkpoint change |
| Train only `nf` | Easy parameter mask | Only 64 final gains; low adaptation capacity and still affects all contexts |
| Freeze `tok` and train backbone | Easy gradient mask | Backbone can still rotate hidden states toward the unchanged EOS vector |
| Frozen-teacher rehearsal | One soft-target loss in isolated harness | Preserves behavior only on the 16 selected rehearsal texts |

Choose frozen-teacher rehearsal as the next single controlled weight-training
slice. It directly penalizes movement away from original token distributions,
keeps the architecture/checkpoint format intact, and uses existing local native
code with no dependency or remote-model requirement. Selection is provisional
until the exact EXP-05 replay results are interpreted.

## Predeclared candidate design

Freshly restore the exact original 132,160-parameter checkpoint and fresh
moments. Keep EXP-05 seed, answer batches/order, text batches/order, 128 steps,
learning-rate schedule, clipping, decay, tokenizer and all datasets unchanged.
No additional training data and no capacity/evaluation prompts in the loss.

Objective:

`L = mean(answer label + EOS CE) + 0.1 * mean(replay hard CE)
     + 1.0 * mean(KL(original || candidate) on replay positions)`.

The KL mean covers the pooled positions of the same two replay examples in
the batch, including the final content position. Teacher probabilities are
precomputed exactly once from the untouched original model for all sixteen
replay sequences, using BOS + content inputs and the full vocabulary at every
position. The teacher performs inference only and stays frozen. No sampling,
temperature change, label restriction, extra RNG draw or EOS-specific logit
hack is introduced. The KL coefficient is fixed at 1.0 before the run and is
not tuned against the visible DEV or continuation probes.

This differs from EXP-05 replay only by its frozen-teacher regularizer. It is
not compute matched: report teacher precomputation time, training time and
memory separately. Bound Java to 768 MiB, one CPU and 660 seconds externally.
If timeout or nonfinite values occur, preserve them as failures; no longer run
or silent hyperparameter change.

## Minimal native implementation

Add a soft-target loss only inside the isolated research harness; keep
production sources untouched. Use stable log-softmax from existing kernels.
For each position, evaluate `sum(q * (log(q) - log(p)))` and backward
`(p - q) / pooledReplayPositions`; q is the frozen teacher distribution.
Skip q=0 in the entropy term. Validate q finite, nonnegative and row-normalized.

A research extension may use the existing internal `Tape.record`/`Node` APIs.
If exposing those APIs requires production edits, stop and implement the loss
as an experiment-local compiled source overlay instead. Pin its exact patch,
source hashes and compiled JAR before running. Do not edit the EXP-05 JAR.

## Mandatory preflight checks

1. Analytic soft-target gradients agree with central finite differences on
   small nonuniform logits and multiple rows; use a tolerance declared in the
   test before execution.
2. Teacher-versus-identical-student KL and logit gradients are near zero.
3. One-hot teacher loss and gradients agree with existing hard cross entropy.
4. Duplicating rows and doubling the normalizer preserves mean gradient sum.
5. All teacher tensors and original weights retain identical hashes before
   and after training; only candidate weights change.
6. First answer batches match EXP-05 replay exactly, proving regularizer
   setup did not consume or reorder the schedule's RNG.

## Measurements and stopping interpretation

Retain the exact EXP-05 DEV, known open/search retention, text BPB and four
continuation probes, including every per-case row, before and after training.
Also report mean replay KL and mean teacher/candidate EOS probability on
replay positions as diagnostic quantities, without treating them as new tests.

A favorable exploratory slice requires all of: DEV exact gain at least eight
cases over original; no decrease in original open/search exact match AND
restricted-choice correct count; retained-text BPB increase at most 5%; and
no loss of the original two 32-token continuation successes. Compare against
both EXP-05 arms, and print failures rather than selecting a winner by one score.
This strengthens EXP-05's retention check because its exact-match baseline is
zero. It is a new prospective criterion, not a retroactive redefinition.

Even a favorable result is visible DEV only. Baseline full capacity fails,
these 16 rehearsal texts cover little language, and neither broad conversation
improvement nor production readiness is established. No app-model promotion.
