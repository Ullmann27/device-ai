# Prospective gradient-access diagnostic

Parent evidence: `33299cd429daacfe79cc392f728465aad14eb59e`.
No optimizer is constructed or called. No trained candidate or checkpoint is
written. This studies differentiability and first-step access, not quality.

## Three fixed profiles

- S0: actual frozen 132,160-parameter initial checkpoint.
- W0: actual frozen 6,562,304-parameter initial checkpoint.
- D0: `LmWeightSurgery.deepenMinGru(W0, 1, 20261104)` in private memory,
  exactly 9,712,128 parameters. Its extra residual outputs start at zero.

Use the same frozen v3 runtime and surgery implementation. Before a backward
pass, compare each profile's next-token probabilities with S0 on all twelve
existing engineering prompts, maximum absolute difference `1e-5`. D0 must
also agree bitwise with W0 on these probability vectors. Any failed gate
stops the study before further backward passes.

## Fixed backward passes

For each profile, use the first four `general` texts already fixed in
`probes.json`. Sequence: BOS + text tokens + EOS. Predict every next token,
with mean cross-entropy normalized by the number of targets. No dataset or
final holdout is read. These are engineering loss probes, not a new corpus.

Record every parameter tensor's size, L2 norm, gradient L2 norm, maximum
absolute gradient, exact nonzero-gradient count, and relative gradient norm.
Record the loss, target count, total gradient norm and all finite-value checks.
For D0, expect first-pass zero gradients in the eight upstream tensors of the
new layer, and nonzero gradients in its `wo`, `w2`, and `b2`. This is a test
of the registered identity-layer construction, not a bug classification.

## Chain-rule checks

For W0, pull its gradients back through the known eightfold widening map and
compare all 132,160 original coordinates with a separately computed S0
gradient. Sum duplicated embedding/gain/bias gradients; divide the summed
dense-matrix gradients and final-gain gradients by eight. Require maximum
absolute error `2e-4` and relative L2 error `2e-4`. The map includes paired
initial noise as a source-independent additive term.

For D0, separately compute W0 gradients on each same sequence. All common
parameter gradients must agree bitwise because the added residual layer is
initially an identity. The new layer's gradients are measured separately.

## Directional derivative checks

For each profile and each of the four texts, check `nf`. For D0 also check
`l2.wo` and `l2.w2`: twenty fixed checks in total. Temporarily perturb a private
tensor in the direction of its normalized gradient by +/-`0.001`, evaluate
the mean NLL, then restore it in `finally`. Compare the central difference
with the analytic derivative along the actually representable Float32
displacement. Tolerance is `0.0005 + 0.02 * abs(analyticDerivative)`, fixed
before measurements. All original arrays must hash identically afterward.
No finite-difference tensor is a selected or serialized model candidate.

## Budgets and evidence

One process at a time, at most 120 seconds per profile and 360 seconds total.
Gradient processes use at most the existing 3 GiB research heap budget, with
a 3 GiB process-RSS gate. This separate backward-analysis budget does not
change the inference limit of 768 MiB, timing gates or quality requirements.
Bind new sources, build, existing runtime, inputs and this protocol before
execution. Save every receipt, including failures. Existing S1/W1 remain
frozen and are not inputs to this diagnostic. No continuation training is
authorized by a positive diagnostic result.
