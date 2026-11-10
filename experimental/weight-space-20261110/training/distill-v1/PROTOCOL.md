# EXP-12: fixed frozen-teacher rehearsal

Status: PREPARED / NOT_RUN. Open diagnostic development experiment.
This follows the two fixed EXP-05 arms. Their answer-only and text-replay
results are known, including continuation collapse and open/search loss.
There is exactly one new arm; no sweep, winner selection or model promotion.

## Hypothesis and objective

The model ties input embeddings and output projection in `tok`. Shared
parameter adaptation may spread short-answer EOS preferences into other
contexts. This is a plausible mechanism, not a demonstrated causal account.
A frozen original teacher penalizes distribution drift on the existing
sixteen replay statements without changing model shape or checkpoint format.

Loss is mean answer label/EOS cross entropy + 0.1 times pooled replay-token
cross entropy + 1.0 times pooled replay-position KL(original || candidate).
The original teacher is separately decoded, its parameter arrays cannot
alias candidate arrays, and only inference tapes produce cached probability
matrices. Cache positions use BOS + replay content and full vocabulary at
temperature one. DEV, open/search retention, retained text and continuation
prompts never construct teacher targets and never enter an optimizer update.

## Fixed controls

`controls.json` freezes seed, 128 updates, batches, learning rate, warmup,
cosine decay, clipping and the new coefficient. All EXP-05 answer and replay
schedules remain unchanged. Each arm starts from original weights and fresh
moments. Extra SCHEDULE rows log the actual answer IDs and replay indices for
independent schedule checking. No extra RNG draws are introduced.

CPU count one, heap 768 MiB, native training-loop deadline 600 seconds, entire
process external deadline 660 seconds. Teacher precomputation time is logged.
This arm uses additional compute and is not compute matched to EXP-05 replay.
Input, source, JAR and compiler identities must be published in a registration
before `distill` mode runs. `inspect` only validates input encodings/masks;
it performs no model inference, teacher caching or optimizer update.

## New loss validation

`LmDistillChecks` runs fifteen bounded checks without any model checkpoint or
optimizer. They cover central finite differences on twelve logits; identical
teacher/student zero KL; shift invariance; one-hot equivalence to existing CE;
shared-logit mixed-objective sum; accumulation into existing gradients;
nonunit upstream scaling; duplicated-row invariance; divisor scaling; teacher
immutability; invalid teacher probabilities/mass; nonfinite logits; and extreme
logit stability. Finite difference step is 0.001; absolute tolerance 0.0002
plus relative tolerance 0.003. Other equivalence tolerances are 0.000002.

Float teacher row sums are validated within 0.00001. Backprop uses the exact
finite-precision derivative `(sum(q)*p-q)/normalizer`, matching the forward
loss even when rounding makes sum(q) slightly different from one.

## Evidence and prospective criterion

Preserve the entire raw stdout, stderr, execution receipt and candidate bytes.
Unchanged original teacher parameters and cached probabilities are required
by canonical SHA-256 before and after the run. Parameter digest uses ordered
matrix name/rows/cols plus big-endian Float32 values, excluding metadata.
Cache digest uses the same representation with replay indices as names.
Independent weight comparison must show changed finite candidate tensors.

Keep every original per-case measurement and compare original, both EXP-05
arms and this arm. Favorable exploratory development requires all of:

- At least eight more exact DEV matches than original.
- No decline in original open/search exact matches and at least 32/64
  restricted-choice correct cases; this prevents the known zero-exact floor.
- Retained-text BPB at most 1.05 times original.
- Both original successful continuation probes, IDs 2 and 3, individually
  still reach 32 tokens. A count alone cannot hide changed successful IDs.

The original baseline reaches 32 tokens in only two of four probes. Therefore
full capacity remains FAIL until all four reach 32; recovery of two alone
means preservation of observed baseline behavior, not full capacity success.
Neither gate proves coherent text or general conversation quality.

All criteria above are prospective for EXP-12; EXP-05 criteria and artifacts
remain unchanged. Stop after this one candidate and retain every failure.
No active app model, app source, APK, signature, main branch or paid compute
is changed. Independent confirmation and production promotion remain absent.
