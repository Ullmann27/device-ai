# Answer-only arm: completed independent audit

EXP-05 / RESEARCH_SHADOW. Open DEV only; no model promotion.
The original registered arm completed 128 updates. This audit executes no model.

All 132,160 numeric weights changed; maximum absolute delta is
0.008392803370952606. The independent checkpoint reader validates the internal
checksum, tensor layout, tokenizer and original/final identities. The final
model SHA-256 is `3820d1596dee43a675305645feece6d46b2711f8a03db0be8171055ee45128ed`.

| Measurement | Before | After |
| --- | ---: | ---: |
| DEV exact label plus EOS | 0/64 | 38/64 |
| Open/search exact | 0/64 | 0/64 |
| Open/search four-choice diagnostic | 32/64 | 0/64 |
| Retention text bits per byte | 2.801409 | 2.889189 |
| Generation probes reaching 32 tokens | 2/4 | 0/4 |

Frozen favorable DEV signal: **FAIL**. Exact retention has a floor effect;
unchanged zero does not establish preserved ability. Text BPB rises 3.1334%,
inside the 5% bound, but generation preservation fails. Baseline itself fails
the four-of-four capacity prerequisite. No coherent-language or general
intelligence improvement is established. The trained label distribution is
skewed: 50 class-0 and 14 class-3 restricted predictions.

The original replay protocol remains unchanged. Complete its one registered
run next and retain both arms, including every failing criterion.
The reusable analyzer and its negative tests are being finalized separately.
