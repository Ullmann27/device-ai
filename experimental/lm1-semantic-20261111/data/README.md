# LM1 semantic pilot and shortcut audit

Status: the open shortcut pilot passed. Native parity and frozen file identities
are recorded separately. This package authorizes no model promotion and is not
the independent holdout. Scope: EXP-05 / EXP-12, research only.

## What failed before

At base `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`, the old generator required
every positive directive to start with Bitte/Jetzt and every negative directive
with Nicht. A fixed Nicht filter solved all 128 TRAIN and 64 DEV cases.
Equal byte lengths concealed systematic 1- or 3-token length differences.
The answer candidate achieved 38/64 exact rows but only 6/32 complete pairs;
26 pairs had identical outputs. Replay and teacher preservation each achieved
35/64 rows and 3/32 pairs. These facts indicate inadequate evidence for semantic
generalization, not a proven single causal mechanism.

The old contrast task supervised only labels 0 and 3. Half its two supervised
targets were EOS. Its 16 generic replay texts did not directly preserve the
open/search output behavior. These are concrete design risks; this data audit
does not claim to identify a unique cause of forgetting. The prior 32/64
open/search figure was a restricted-choice diagnostic. Exact baseline answers
were already zero; retaining zero would not demonstrate retained competence.

Historical sources, data, audit programs, metadata and measured outcomes remain
unchanged. `historical-source-inventory.json` binds every file in the old data
directory to exact bytes at the verified research commit.

## Data and labels

`pilot-v1/train.tsv` contains 192 rows, 48 per class and 96 opposing pairs.
`pilot-v1/dev.tsv` contains 96 new rows, 24 per class and 48 opposing pairs.
Every unordered pair of the four classes occurs equally often. Each opposing
pair uses identical words, byte counts and native BPE token counts; the
instructional role of the two action phrases changes. Original rows and
newly generated rows never overlap exactly after Unicode normalization.

| Label | Bounded requested behavior |
| --- | --- |
| 0 | No listed file, search or note action |
| 1 | Open the named file |
| 2 | Search for the named term |
| 3 | Store the named note |

Inputs are `BOS + encode("Auftrag: " + prompt + "\n")`; target is the existing
single label token followed by actual EOS. Prefixes use at most 96 TRAIN and
104 DEV tokens, below the unchanged 256-token context. Label IDs are 48–51;
BOS/EOS are 510/511. The byte-level tokenizer has no unknown-token symbol;
this is not evidence that it understands new words.

TRAIN has four construction families: quotation scope, corrective alternative,
double negation and changed request. DEV uses conditional branches, ordinal
references, authorization scope and description versus instruction. TRAIN and
DEV have separate named objects, with negation-bearing names in both splits.
No owner data or old DEV examples are included. All examples are newly authored
synthetic German, not collected human language or real tool commands.

The families are structurally different, while the four intent meanings and
basic verbs remain shared. Named-object transfer is tested; unrestricted
transfer across arbitrary new task types is not established. Only six object
names occur across TRAIN/DEV, and template repetitions are correlated.

## Pilot controls fixed before neural training

Ten controls use no LM1 weight updates. Learned controls fit TRAIN only;
hyperparameters are fixed in `audit_semantic_pilot.py`. DEV is visible data
engineering feedback, never the independent holdout. The prospective pilot
rejection threshold is any declared trivial baseline reaching 90% DEV accuracy.

| Control | TRAIN correct / 192 | DEV correct / 96 | DEV complete pairs / 48 |
| --- | ---: | ---: | ---: |
| Majority class | 48 | 24 | 0 |
| Legacy Nicht filter | 60 | 31 | 0 |
| Negation-count parity | 52 | 30 | 0 |
| First action phrase | 96 | 48 | 24 |
| Last action phrase | 96 | 48 | 24 |
| Word unigram logistic | 96 | 48 | 0 |
| Word bigram logistic | 192 | 49 | 4 |
| Character 3–5 gram logistic | 170 | 46 | 7 |
| Word bigram naive Bayes | 192 | 48 | 3 |
| Length/count depth-3 tree | 90 | 35 | 0 |

The strongest measured DEV row score is 49/96 (51.04%); the declared pilot
gate passes. This is not proof of semantic understanding or absence of every
shortcut. First/last-action controls reach 50% complete pairs, so pair success
must be compared against those controls as well. Unigram and length-only
oracles cannot exceed 50% because every pair has conflicting labels and
identical corresponding features. Bigram perfect TRAIN accuracy exposes the
continuing possibility of template memorization, despite weak DEV transfer.

`shortcut-audit-v1/report.json` records class/family results, raw-prediction
hashes, versions, feature ceilings and lexical novelty. `predictions.json`
contains every control prediction. These are real executed diagnostics.

## Native tokenizer and verification

`frozen_tokenizer.py` is an independent Unicode-aware port of the existing
tokenizer; it never changes merges. `LmSemanticTokenizerAudit.kt` compiles the
unchanged native Kotlin tokenizer and checks all 288 prefix token arrays,
round trips, label tokens, BOS/EOS and context limits. Use the Kotlin 2.4.0
receipt as the release-of-data gate; an earlier 2.0.20-only tokenizer check is
preserved as a separate diagnostic, not silently substituted.

Eight Python checks cover reproducibility, class balance, pair feature bounds,
foreign tokenizer refusal, split contamination, refusal to overwrite datasets,
pair-failure behavior under class collapse and action-order controls.
No LM1 model inference, training update, cloud GPU or paid API was performed
by this data bot. Native compile/run elapsed seconds are available in their
receipts; total chat or credit cost is not exposed and is not estimated.

## Reproduction

```sh
python3 experimental/lm1-semantic-20261111/data/make_semantic_pilot.py --check
python3 -m unittest discover -s experimental/lm1-semantic-20261111/data -p test_semantic_pilot.py -v
python3 experimental/lm1-semantic-20261111/data/make_semantic_pilot.py --output /tmp/lm1-new-data
python3 experimental/lm1-semantic-20261111/data/audit_semantic_pilot.py --data /tmp/lm1-new-data --output /tmp/lm1-new-audit
```

Use fresh output directories. Native parity commands, inputs, source hashes,
compiler version and JAR hash are bound in `native-tokenizer-2.4.0/receipt.json`.
The output JAR is reproducible build material in scratch; sources, inputs and
all measurement evidence are committed here. No holdout file was accessed.
