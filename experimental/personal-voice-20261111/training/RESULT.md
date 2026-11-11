# Personal voice LM1 training: two bounded candidates completed

Status: both native runs COMPLETE; independent quality verdict REJECTED for both.
Research only, EXP-05 / EXP-12. No app/Android/APK/model selector change.

## What actually ran

Exactly two candidates began independently from the unchanged original
132,160-parameter LM1 minGRU. Each performed 128 native AdamW updates.
The original tokenizer, architecture and 256-token context stayed unchanged.
The same seed and all 128 supervised/replay draw schedules are byte-identical
between arms; the second arm adds only original-model KL preservation.

Both use 128 balanced contrast TRAIN examples, 32 separately authored simple
open/search replay examples and 16 existing public text replay sequences.
Native inspection found a maximum prompt prefix of 68 tokens. Historical
64-case retention and independent holdouts never enter updates. All speech
examples are synthetic; no owner's recording or personal transcript was used.

The native source/build was published at
`e83536a324f734eacd76e418a899826b06f408bc`. Final run registration was published
at `e6dc45cd061a17ca5fa7409fa416ff6d2a61b7c2` **before the first update**.
Data was frozen at `565c71ed63ca16c3891b4a7989eca82bf419ac6f`; independent
protocol at `9deac1792e79736167c5169c916941d45c8f9a96`. Bot 3 separately
captured original historical retention/length/EOS before training and
withheld all independent test results from the training agent.

## Actual artifacts and resources

| Arm | Updates | Wall seconds | User CPU seconds | System CPU seconds | Peak RSS KiB | Bytes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| balanced-replay | 128 | 26.176934 | 27.718781 | 0.488150 | 119768 | 529634 |
| balanced-preserve | 128 | 38.703392 | 41.320978 | 0.459306 | 126928 | 529636 |

Training total: 256 updates; 64.880326 wall seconds; 69.039759 user CPU seconds;
0.947456 system CPU seconds. Largest individual process peak: 126,928 KiB
(123.953125 MiB). These measure the two Linux x86_64 JVM processes only;
compilation, evaluation and assistant compute are excluded. No training GPU
job, paid compute request or external teacher/model API call was made.
No Fold7 latency, temperature, energy or ASR measurement was available.

Original SHA-256:
`7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`.

- `artifacts/balanced-replay/final.lm1`:
  `e1e231cc2b30f2c8a36af0b0b9c7edcfbc13a19ae6e73faee501448d43f511fe`.
- `artifacts/balanced-preserve/final.lm1`:
  `4d0cb91226970737bd3413736b66d8688714f2c65ae0e383501aadd6bd5f763f`.

Trainer-side independent binary parser found valid checksums, identical model
shape/tokenizer, finite values and 132,160 numerically changed weights in each
candidate. The original baseline and every registered input remained
unchanged. The second arm's original teacher parameters and probability cache
have equal before/after hashes. These are technical integrity findings,
**not evidence of improved understanding**. Bot 3 performs a separate audit.

## Reproduce and inspect

`registration.json` records exact inputs/hashes, commands, optimizer controls,
source/compiler binding and resource limits. `runs/<arm>/stdout.tsv` preserves
all 128 sampling schedules and updates. `runs/<arm>/receipt.json` binds logs
and actual output model bytes. `training-identity.json` compares real tensors.

Use the existing pinned builder from PR #728 with the new
`LmPersonalVoicePilot.kt`, the existing `LmSoftTargetKl.kt` and
`LmDistillChecks.kt` as extra sources. `build-evidence/build.json` contains the
exact successful build command and compiler identities. Restore originals
from the repository paths in `PROTOCOL.md` and the data from the above data
commit. Rebase only local execution paths when reproducing elsewhere; verify
every source/data/model hash and archive that new invocation separately.
Do not silently rewrite the archived registration or original receipts.

No duplicate training rerun or third candidate was performed. Byte-for-byte
reproduction in another environment has not yet been tested. The recipe,
inputs and schedules are available for independently authorized reproduction.

## Limits and next decision

The four-label specialist covers no action / open / search / remember. It
cannot independently express all question-vs-statement distinctions,
parameters, references, multi-action plans or natural answers. Acoustic ASR
was not trained. Existing speech/context/memory integration needs its own
A/B/C evidence. LoRA is not implemented in this native engine; adding it or
adapting a larger model requires a separately supported training/export path.

Neither candidate is authorized for the app. Keep both artifacts research
only; preserve original/Gemma checkpoints and all release/signing settings.
Stop this round at two candidates and defer the quality decision to the
unchanged independent protocol. A better training loss or altered weights
cannot overrule old-skill, negation, hazard, length or held-out failure.

## Independent outcome after both candidates were frozen

Bot 3 evaluated the unchanged 64-case independent set only after receiving
both final model hashes. Both candidates are **REJECTED**. No additional
training, hyperparameter change or data revision follows this result.

| Independent metric | Original | balanced-replay | balanced-preserve |
| --- | ---: | ---: | ---: |
| Exact label plus EOS | 0/64 | 17/64 | 17/64 |
| Restricted four-label accuracy | 16/64 | 17/64 | 17/64 |
| Complete opposite pairs | 0/32 | 1/32 | 1/32 |
| Identical-word-bag opposite pairs | 0/17 | 0/17 | 0/17 |
| Historical open/search restricted retention | 32/64 | 18/64 | 25/64 |
| Four text-generation lengths | 7 / 4 / 32 / 32 | 0 / 4 / 2 / 1 | 6 / 4 / 3 / 1 |
| New valid forbidden-action proxy predictions | — | 16 | 16 |

Exact-output improvement mainly reflects learning the numeric-label/EOS
format: the original produced 64 invalid-format outputs, whereas both trained
models produced zero invalid-format outputs. Restricted accuracy improved by
only one case and both failed every identical-word-bag pair. Supervised replay
and original-model KL did not preserve the old task score or generation
lengths. The proxy failures are incorrect model outputs in offline evaluation;
no actual device action was performed.

Independent raw logs, scoring protocol, metric definitions and final report
are maintained by Bot 3 under the same research directory's `evaluation/`
subtree in its separate branch/PR #755 and the research integration branch.
This rules out a successful semantic-learning claim for this round. Retain
only the reproducible experiment, data-quality and safe correction-learning
work for later review; exclude both weight files from the APK.
