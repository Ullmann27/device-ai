# Independent distillation audit plan

Status: written before the new candidate is trained or evaluated.

Scope: one fixed, exploratory DEV continuation from the authentic original
132,160-parameter checkpoint. The original answer/replay reports stay frozen.
No production promotion and no independent generalization claim.

## Implementation checks

- Teacher and student must be separately decoded from the same original bytes.
- Teacher probabilities must come from inference-only tapes without updates.
- Teacher parameter identity must match before and after the experiment.
- Distillation is KL(teacher || student), with positive coefficient 1.0.
- For teacher row q and student softmax p, the logit derivative is
  `(sum(q) * p - q) / pooled_replay_positions`.
- The value and gradient use the same pooling denominator and no vocabulary
  averaging. This accounts for Float32 teacher rows not summing exactly to one.
- The student tape records the KL derivative and preserves ordinary native
  autodiff operations. Finite-difference and accumulation checks are required.
- Answer sampling, seed, 128 updates, optimizer settings and 0.1 replay loss
  remain identical to the original registered replay arm.

## Evidence checks

Publish protocol, source/input/compiler/JAR identities and exact command before
training. Validate run receipt, complete raw logs, original checkpoint identity,
trained checkpoint checksum, direct numeric weight deltas, and frozen teacher
identity. Reject missing/duplicate cases, wrong labels, forged exact scores,
aggregate disagreement, missing updates/probes, or nonfinite metrics.

Require the same 135 baseline measurement records as both previous arms. Reuse
the original independent per-case metric aggregation after explicitly checking
every added distillation field; do not silently discard unknown fields.

## Metrics and interpretation

Preserve the original frozen criteria verbatim. Its baseline-all-four capacity
prerequisite is already false (2/4), and must remain reported as false. Therefore
the original combined favorable signal cannot be relabeled PASS by this run.

Report separate, explicitly new EXP-12 development criteria against the original:
restricted opening/search accuracy at least 32/64, and both originally successful
generation probes (IDs 2 and 3) still reaching 32 tokens. Keeping only the count
at two could conceal the loss of an original successful probe. Candidate all-four
capacity is a distinct target. These prospective DEV criteria are not an
independent generalization or production acceptance gate.
The exact opening/search score is 0/64 at baseline; unchanged zero is a floor
effect and does not demonstrate preservation.

Report note/no-action exact scores separately by class, text bits per byte,
all generation lengths, and every failed criterion. Favorable text loss alone
does not establish coherent generation or broad language understanding.
