# Contrast-pair diagnosis

Post-hoc analysis of the frozen answer-arm raw log. No inference or training
was performed by this diagnostic. Exact output flags are independently
recomputed from generated label tokens and EOS termination; missing,
duplicated or mislabeled DEV cases are rejected. Dataset/tokenizer hashes
must match the raw run's INPUT records. Metadata must cover each case once.

| Measure | Original | Answer arm, 128 updates |
|---|---:|---:|
| Exact DEV rows | 0/64 | 38/64 |
| Both opposing labels correct | 0/32 pairs | 6/32 pairs |
| Identical output within pair | 32/32 pairs | 26/32 pairs |
| Exact class 0 | 0/32 | 28/32 |
| Exact class 3 | 0/32 | 10/32 |
| Directive first | 0/32 | 16/32 |
| Directive last | 0/32 | 22/32 |
| Short content | 0/32 | 19/32 |
| Long content | 0/32 | 19/32 |

The row-level gain mostly reflects acquiring a label/EOS output format and
a strong class-0 preference. Only six opposing pairs are both correct. This
does not establish robust interpretation of affirmative and negative orders.

All paired prompts have equal UTF-8 byte counts. Their token counts differ:
class 0 has one fewer prefix token for half the pairs and three fewer for the
other half, in both TRAIN and DEV. Counts use the already frozen Python port
of the tokenizer; no new native-tokenizer process was run. These differences
are confounds, not evidence that this model uses length as a shortcut.

A post-hoc fixed rule, `contains Nicht => 0; otherwise => 3`, solves every
TRAIN and DEV row. This confirms the narrow marker shortcut already noted
in the dataset documentation. It is not a model result or a proposed runtime
rule. Position/family differences are descriptive, not causal conclusions.

Next data requirement: a separately frozen diagnostic containing negation
words in quoted content, affirmative paraphrases without Bitte/Jetzt, and
counterbalanced token lengths. Do not alter this consumed DEV set or present
it as fresh confirmation. Evaluate known-task retention and generation
capacity separately before considering any model promotion.

Reproduce from a checkout containing the existing frozen data package:

```sh
python3 analyze_contrast_pairs.py --data PATH_TO_DATA --log PATH_TO_STDOUT_TSV --tokenizer PATH_TO_TOKENIZER_LMBP
python3 -m unittest -v test_contrast_pairs.py
```

`answer-pair-audit.json` binds the diagnostic source, imported tokenizer port,
raw log, tokenizer, metadata and data by SHA-256. All analysis is open DEV,
with no independent-test or application-quality claim.
