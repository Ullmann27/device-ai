# LM1 open development data

This package supports one small note-versus-no-action weight-training pilot.
It is developer-visible diagnostic data, with no blind TEST or general
intelligence claim. Old consumed TEST cases are not reused as confirmation.

`make_note_contrasts.py` generates 128 TRAIN and 64 DEV cases. Every combination
of family, object, content length and directive position has two equal-byte
prompts, one label `0` and one label `3`. TRAIN and DEV use disjoint full
directives and object contents. Within each split, the two labels have exactly
the same prompt-byte-length distribution. Labels, short/long content and
directive-first/directive-last positions are balanced. These pairs deliberately
share meanings and the Bitte/Jetzt/Nicht contrast; a marker-only rule can solve
the task. They do not establish general language understanding.

The native input wrapper is `Auftrag: {prompt}\n`. Supervise only one label
token and actual EOS, never prompt tokens. The tokenizer is frozen; native
validation must establish single-token labels, all sequence lengths and the
real EOS ID. Equal UTF-8 lengths alone do not imply equal BPE token lengths.

`retention.tsv` contains only classes 1/2 from historical TRAIN at PR #508,
commit `59891cfad6dd015f47c187cc688ea94b0026ad7d`. It is a known-task retention
probe, not new independent confirmation. It must not enter this pilot's updates.
The source TRAIN hash and selected-row provenance are bound separately.

`replay.txt` has sixteen newly authored generic statements for the replay arm.
One line is one sequence. `retention_text.txt` has eight different statements
for open language-retention diagnostics and never enters updates. These small
corpora share broad language and mathematical topics. No document-level or
semantic independence is claimed. No private user information is included.

Generate into a new directory:

```sh
python make_note_contrasts.py --output note-contrasts-v1
python -m unittest discover -s . -p test_note_contrasts.py -v
```

The generator refuses existing destinations. All data and model outcomes must
be reported separately. An intent gain cannot replace long-generation capacity
or retention gates, and does not authorize production model promotion.
