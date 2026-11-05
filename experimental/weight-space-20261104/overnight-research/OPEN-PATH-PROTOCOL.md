# Prospective opening of the identity layer's gradient paths

Parent: `16aebc5eea5022de3f18f5adff17cab058168751`.
Question: can all 1,838,592 initially zero-gradient coordinates in the new
layer receive a finite, nonzero gradient after one fixed small path opening?
This is a counterfactual differentiability probe, not an optimizer step.

Use exactly the same D0 construction from the actual frozen W0 checkpoint:
one added layer, seed `20261104`, 9,712,128 parameters. The initial D0/W0
distributions must be bitwise equal on all twelve existing engineering
prompts; W0/S0 must pass the unchanged `1e-5` probability-parity bound.

Perform one and only one private perturbation, independent of observed
gradients or results:

- `l2.wo[j,j] = 0.0001` for `j=0..511`;
- `l2.w2[r,r % 512] = 0.000025` for `r=0..2047`;
- every other entry retains its original value, including zero `l2.b2`.

Exactly 2,560 previously zero coordinates change. Before any backward pass,
the perturbed model must remain within maximum absolute probability change
`1e-5` from W0 on all twelve prompts. Failure stops this attempt; do not try
another amplitude. Restore the two original tensors in `finally`.

If the gate passes, compute the same mean next-token cross-entropy gradients
on the four existing general probes used in milestone 5. Record all 35
gradient tensor norms, minima/maxima and exact nonzero-coordinate counts per
probe. The prospective access criterion is all 9,712,128 gradient coordinates
finite and nonzero on each probe. Report failure honestly if any coordinate
remains zero. A very small nonzero derivative does not prove useful learning.

Finally require exact initial/final parameter digests, unchanged parent arrays
and unchanged original input files. No optimizer, training update, serialized
checkpoint, candidate selection, dataset split or consumed final test.

One serial JVM process, 120 seconds, 3-GiB heap and 3-GiB process-RSS gate,
matching the preceding backward-analysis limits. Existing inference and model
quality requirements remain unchanged. Source/build/input identities are
frozen before execution; preserve raw failure evidence and originals.
