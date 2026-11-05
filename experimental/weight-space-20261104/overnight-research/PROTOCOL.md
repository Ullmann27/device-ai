# G1: channel diversification and EOS localization

Status: prospective diagnostic protocol, before new measurements.
Parent: `0af14f75b04e68ba849a92316b8be51041e8738b`, PR #492.
Observed PR #508: `59891cfad6dd015f47c187cc688ea94b0026ad7d`.
Scope: offline research diagnostics. No optimizer, model selection or promotion.

## Questions and ordered milestones

1. Measure whether the eight channel replicas in W0/W1 remain equivalent.
   Read the four already frozen S0/S1/W0/W1 checkpoint binaries. Check their
   exact identities, checksums, finite tensors and layouts first. Report
   ordinary replica spread and, for dense matrices, spread after summing
   input replicas. The latter tests the action on identical input replicas;
   paired initialization noise is not itself proof of different functions.
2. Trace native minGRU activations and next-token EOS probabilities on the
   twelve fixed engineering prompts in `probes.json`. Compare the diagnostic
   forward pass with the existing model's logits on every measurement.
   W0 versus S0 probability parity uses the existing tolerance `1e-5`.
   Replica variation is reported continuously, without an invented threshold
   for learned independence or an "effective parameter count".
3. Localize changes using every predefined block replacement in both
   directions, separately for the small and wide pair. The disjoint blocks
   are `tok`, `nf`, all `l0.*`, and all `l1.*`. This gives four native arms
   and sixteen interventions. Replacements are private in-memory copies;
   no new checkpoint is serialized. These are diagnostic interventions,
   not candidates. Record all results, including failed and harmful ones.
4. Verify source binding, rejected invalid resumes, complete output
   accounting, unchanged input hashes and numerical forward parity. Publish
   a reproducible report and a specific next research decision.

## Fixed measurements

For all 20 arms and 12 prompts: input token IDs, next-token top ID, EOS
probability/rank, EOS-minus-best-other logit margin, distribution entropy,
and all eight replica activation spreads at embedding, both recurrent
residuals, both MLP residuals and final normalization. Small models have
one replica and are reported as such. Store all 512 next-token probabilities
to permit independent arithmetic checks. Do not interpret synthetic prompts
as semantic quality measurements.

For the four native arms only: greedy generation with the existing runtime,
at most 32 new tokens and 5,000 ms per call, including stop reason and actual
token count. This is a new diagnostic workload, not a replacement for the
registered capacity benchmark or its unchanged acceptance requirements.

Compare native W0/S0 distributions. Report each intervention's change from
its own receiving arm, with all twelve pairs visible. No winning intervention
is selected, retained as a model, or tested on the consumed final holdout.
Block substitution can break co-adapted representations. It is evidence of
dependence within these checkpoints and prompts, not proof of a unique
training cause, broad capability or generalization.

## Resource and evidence rules

- Existing frozen v3 runtime JAR and Kotlin standard library are hash pinned.
  The new harness is compiled separately against that JAR; production source
  is never changed. Record compiler identity and source/JAR hashes.
- One process at a time; diagnostic JVM heap at most 1 GiB, 120 seconds per
  arm, 1,800 seconds across the complete new inference study. A timeout is a
  preserved result, not permission to increase the budget or retune prompts.
- Each new output directory is exclusive. Resume may reuse only exact
  completed, source-bound receipts and unchanged files. An incomplete or
  failed arm stays blocked for continuation; no silent overwriting.
- Bind source, protocol, probes, archive, tokenizer and tools before the
  first actual inference. Verify all input hashes again afterward.
- Structural diagnostics use the previously independently checked binary
  reader, hash pinned, and installed NumPy; no dataset is opened.
- The existing 48-case final holdout is consumed. Neither its dataset nor
  examples are inputs to this work. Existing final scores stay unchanged.
- No training from S1/W1: both fail the registered capacity criterion.
  Old quality/resource gates and all historical evidence remain unchanged.
- Any failed integrity/parity gate prevents further experimental execution.
  Preserve originals and failure evidence. No main merge or production run.

## Prospective limits

The protocol is declared before these diagnostics, after earlier quality and
capacity failures are known. It is exploratory follow-up, not a fresh blind
quality assessment. Numeric parameter count remains 132,160 / 6,562,304;
neither exact-value diversity nor activation spread measures knowledge.
