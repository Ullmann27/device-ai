# Personal voice LM1: bounded native training protocol

Status: native source COMPILED; training NOT_RUN. EXP-05 / EXP-12; research only. No app integration.

## Verified starting point

- Research base: PR #728, `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`.
- Original model from `d2304a7658e1256b160c8012e1bac28268ff64a5`:
  `checkpoints/7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd.lm1`.
  SHA-256 equals basename, 529,297 bytes, 132,160 float32 parameters.
- Original tokenizer from that commit:
  `tokenizer/tokenizer-v512-94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f.lmbp`.
  SHA-256 equals embedded digest, 2,040 bytes.
- Architecture: minGRU, vocabulary 512, dimension 64, two layers, context 256,
  MLP width 256. Tied token/output matrix; no separate classifier head.
- Reuse the pinned #530 native sources and isolated continuity patch through
  `experimental/weight-space-20261110/training/build_experiment.py`.
- Locally available Kotlin 2.4.0 and Java 21. Record actual compiler/source/JAR hashes.

## Contract and limits

Four original labels: `0` no action, `1` open, `2` search, `3` remember.
Input TSV header `id\tlabel\tprompt`; model input BOS + `Auftrag: {prompt}\n`.
Require prefix token count + 4 <= 256; no silent truncation.
Exact free answer requires one correct label token followed by EOS within four
steps. Restricted four-label argmax is supplementary, never interchangeable
with a valid generated answer or full semantic understanding.

Long utterances, conversation references, multiple tasks and parameter
extraction belong to the existing pipeline evaluation. Four labels cannot
represent all those meanings, and success on this specialist task does not
prove general speech understanding or acoustic ASR improvement.

At most two candidates, each from unchanged original bytes and fresh AdamW
moments. Research-lead authorized controls (data/evaluation gates still required): 128 updates,
seed 20261111, maximum learning rate 0.00005, four warmup steps and cosine decay,
weight decay 0.01, gradient clip 1, one worker, Java heap 768 MiB, process
deadline 600 seconds per candidate. No parameter search, external teacher,
paid GPU, extra API usage, new tokenizer, LoRA or extra model head.

Proposed candidates:

1. Balanced supervised examples across all four original labels, with explicit
   old-style open/search supervision and full-text replay weight 0.25.
2. Identical supervised/replay schedule plus native KL preservation against
   the original model's distributions on those same public replay texts.
   Teacher remains frozen and separately loaded; validate no tensor aliasing.

The second arm is an original-model retention control, not distillation of a
larger proprietary model. Public synthetic research data only. No user audio,
private transcript, cloud teacher, live user data or new inference routing.

## Gates before first optimizer update

1. Bot 1 data contract/labels/quality audit fixed, all labels represented,
   no training/holdout content leakage, contrast pairs and shortcut audit.
2. Bot 3 independent protocol and holdout hashes committed before results.
3. Research lead authorizes concrete two-arm budget and data quality.
4. Compile, inspect original bytes/token lengths, verify native KL checks.
5. Commit/push code, controls, source/data identities and registration.

Training code must never open independent evaluation files. Each output goes
to a fresh directory; retain update schedule, logs, output model bytes and
complete resource receipt. On failure stop that arm and preserve evidence;
no silently changed retry. Evaluator measures baseline and both frozen outputs
independently, including negation hazards, old tasks, text BPB, EOS and lengths.

## Disposition and rollback

No candidate is approved by a lower training loss or changed weights. Only
independent unseen performance and retention gates can recommend later
review. Original/Gemma/app selectors remain unchanged; rollback is keeping
all candidate files outside the production package. No main write, APK,
signing, release, installation or branch deletion.

## Implemented sampling and compilation receipt

The answer batch contains eight examples, exactly two per label. Labels 0/3
each use two contrast-training examples. Labels 1/2 each use one contrast
example and one newly authored simple supervised replay example. Historical
`retention.tsv` contains all original open/search TRAIN rows, so none of those
rows are used for new training. Full-text replay uses two original public
`replay.txt` texts per step, weight 0.25. Both candidates have exactly the same
seed and draw schedule; only original-model KL retention differs.

`LmPersonalVoicePilot.kt` compiled with the pinned builder; source and compiler
identities are in `build-evidence/build.json`. The existing 15 native KL checks
passed with zero model updates, including numerical gradient and non-mutation
checks (`build-evidence/kl-checks-receipt.json`). No DEV or test scores have
been read or produced by the training agent. Training reads only explicit
TRAIN, supervised replay and public text replay paths. The separate `eval`
mode performs zero optimizer updates and records raw probabilities/outputs.
