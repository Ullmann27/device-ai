# Original-weight continuity experiment

Status: WIP / UNEXECUTED. EXP-05 and EXP-12, research only.

This new experiment loads the authentic 132,160-parameter LM1 baseline
from repository commit `d2304a7658e1256b160c8012e1bac28268ff64a5`:
`checkpoints/7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd.lm1`.
Expected SHA-256 matches its filename; size 529,297 bytes.
Tokenizer `tokenizer/tokenizer-v512-94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f.lmbp`
comes from that same commit; size 2,040 bytes.
Both original bytes were downloaded through the authorized GitHub connector
and their SHA-256 values verified before experiment construction.

Native sources and the isolated training-state patch come from #530,
`4b8fa96effd5a390bd5fd83ed84b81ac850b1d5b`. Production sources stay unchanged.
The new compiler is Kotlin 2.4.0 / Java 21; historical 2.0.20 build identities
are not reused. Build output records source, patch, compiler and JAR hashes.

## Fixed experiment

- Two synthetic technical sequences, never presented as language-quality data:
  `Die Lampe ist blau.` and `Das Fenster bleibt offen.`
- Actual tokenizer BOS/EOS derived at runtime; full next-token loss.
- Learning rate 0.00001, decay 0.01, warmup zero, two-step schedule,
  clip 1, one worker. Fresh moments from the unchanged baseline.
- Continuous arm: two updates. Recovery arm: one update, complete LM1T
  save/load, second update. Four actual optimizer updates total.
- Require complete serialized state identity, nonzero first/second moments,
  finite losses and parameters, unchanged original inputs.
- Export initial/intermediate/final LM1C and LM1T states for independent review.
- 132k process deadline 120 seconds, maximum Java heap 768 MiB.
- No final-test data, no claimed model quality, no active-model promotion.

The harness supports the existing 16,011,776-parameter shape, but that arm is
NOT_CONFIGURED and must not run without its own exact input identity and
prospective resource registration. This experiment does not recreate 16M
weights or imply a historical 16M checkpoint is available locally.

Build and input inspection perform zero updates. Actual `run` must wait for
the root agent's remote source/protocol freeze. New output directory required;
input checkpoint, tokenizer and all historical records remain unchanged.
