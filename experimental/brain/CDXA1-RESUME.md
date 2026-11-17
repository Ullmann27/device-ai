# CDXA1 isolated research resume

**UNTRAINED / UNVERIFIED answer quality. Owner stopped session at20%usage.**
No CDXA1 training, train/dev quality evaluation, CSX/CDX comparison or independent
TEST run occurred. No CDXA1 weights exist to preserve. Source unit/numerical
checks passed7/7, including48 finite-difference probes of pointer/UNKNOWN/EOS
targets. The standalone parameter update in a unit test is a disposable numeric
check, not a trained candidate. Every old checkpoint/format remains intact.

Data/protocol were frozen before predictions at`01c828ed`; TRAIN64/DEV62
(54in-family plus8out-of-family challenges). Inference imports no data generator
or answer labels. The model has2064 trainable parameters and uses CSX3-style
engineered overlap/binding/negation/boundary inputs plus word/3–4gram hashes.
It learns every pointer/UNKNOWN/EOS selection with a shared16hidden tanh MLP;
there is no forced next-pointer, forced stop or deterministic fallback.

This is a proposed bounded neural copying primitive, not a repaired general
CDX1 byte language model or learned natural-language semantics. It copies raw
source chunks, so cannot produce a novel output word/paraphrase. A strong
separate deterministic rule comparator is implemented and may equal/beat it.
Existing CSX3 already has related lexical features and a literal span pointer;
this hypothesis adds autoregressive pointer transitions/EOS, not a new runtime.

When resumed and explicitly authorized to continue, read AGENTS, the parent
handoff and experiment contract. Verify official remote SHA/PR ancestry first.
The previously agreed protocol is frozen; do not inspect independent TEST before
candidate freeze. Planned exact standalone commands (NOT_RUN after owner stop):

```bash
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 python experimental/brain/cdxa1_pointer.py \
  config/research/cdxa1-pointer-v1.json NEW_OUTPUT_DIR EXACT_CLEAN_SOURCE_SHA
```

Bound it externally to90s/one CPU thread; NumPy2.3.5 is already installed. The
CLI saves epochs0/10/30/80 and chooses the highest in-family DEV exact+EOS score,
ties fewer epochs. It retains every output, all stages and the deterministic
baseline. Do not change features/hyperparameters based on final TEST.

Before any independent-test claim, still implement/run the frozen fair
comparators on the exact same64TRAIN:

- Actual `ContinuumResidualSpanCore` with protocol flags,30epochs,.025rate,
  explicit goldSpan training annotations and deterministic source-sentence
  expansion of predicted literal spans for output-format fairness.
- Actual `TinyContextDecoder`, seed91/rate.035/120000updates, raw and train-mean
  centered×32 with the frozen SWB1. Its evidence input limit160bytes and corpus
  limits may require explicit exclusion/reporting, never silent truncation.
- Archive baseline/learned/randomized weights, full raw text/EOS/budgets, all
  strata/challenges, source/model/tokenizer/protocol hashes and limitations.

Then freeze selected candidate/source/tokenizer/training data/inference settings
and notify the independent evaluator. Parent holds a new fixture with32in-family
cases(18positive/14UNBEKANNT) plus8challenges. Its SHA was announced as
`58bcb0d6393b10f9af0f064692a71458f1ff8978759fb9b5e86d1b8b0a4e0e7c`.
**Content not opened, predictions not run, fixture unconsumed by this agent.**

Existing measured conditioner evidence is in
`docs/evidence/sol-cdx-20261026/`; no unrelated owner/Gemma/memory file is needed.
No model is connected to the app; rollback by reverting individual research
commits. No main merge or production promotion is authorized by this file.
