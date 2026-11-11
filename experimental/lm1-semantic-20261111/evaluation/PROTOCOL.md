# LM1 semantic transfer: independent evaluation protocol

Status: PRE-TRAINING REGISTRATION; no new model has been evaluated by this agent.
Source: PR #728, `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`.
Scope: EXP-05 / EXP-12, isolated research; never a product activation.

## Fixed contract

Labels are **0 = no action, 1 = open, 2 = search, 3 = create note**.
Input is BOS followed by native-tokenizer encoding of `Auftrag: {prompt}\n`.
The output target is one class token followed by actual EOS, not the literal
characters `EOS`. The original tokenizer and 132,160-parameter architecture
remain unchanged. No evaluated instruction can reach a real tool.

Compare original LM1, historical answer/replay/teacher candidates, and at most
two new candidates A/B on exactly the same frozen cases. If the data pilot
fails, A/B are NOT_RUN; do not fabricate replacements or continue training.
No additional candidate, tuning sweep, or model selection on this holdout.

## Data and access discipline

The independent semantic holdout will contain 128 rows, 64 opposing pairs,
eight syntax families, and 32 rows per class. Syntax templates and objects
must be disjoint from the training and open development sets. Authoring is
synthetic, by a separate agent, not a representative sample of all German.
Its manifest, hashes, source, and labels are committed before model evaluation.
No old DEV data are relabelled as an independent test.

Training/data agents are instructed not to read this directory until A/B
checkpoints and optimizer states are frozen. The coordinator receives only
protocol, schema, identity, and validity status before then. Shared filesystem
and accessible Git branches prevent technical blinding: separation is
organizational and cannot prove absence of accidental access. Record any
access breach; never call this a securely blinded clinical-style trial.

Historical 64 open/search cases remain an exposed retention diagnostic and
are not training data. Add independently authored new open/search prompts.
Report familiar and novel retention separately. New training retention anchors
must not include either evaluation set. Compare text likelihood and greedy
continuations on historical and new text probes; length is not language quality.

## Predeclared semantic success gates

The primary metric is unrestricted-vocabulary model logits restricted only
for scoring to the four established class tokens. It measures four-choice
discrimination; it does not claim fluent answers or valid tool execution.
Free greedy generation is scored separately: exactly one correct class token,
then EOS within four generated steps. Record every raw token and first EOS
probability. A format improvement alone cannot pass semantic gates.

For either new candidate, **all** of the following must hold:

1. Four-choice accuracy at least 60%; both members correct in at least 40%
   of complete opposing pairs.
2. At least 10 percentage points better in both primary accuracy and complete
   pair accuracy than original LM1, every historical candidate, and every
   predeclared trivial control. Reporting the best old model/control is a
   summary of these simultaneous comparisons, not post-hoc model selection.
3. Paired 97.5% bootstrap lower confidence bound above zero for both effects
   against each comparison. Two candidate tests use this conservative level.
   Resample whole opposing pairs, never dependent rows separately. Use 20,000
   draws and seed 2026111103. Also report leave-one-family-out differences and
   family-cluster bootstrap sensitivity; template dependence limits inference.
4. No syntax family loses more than 25 percentage points versus original.
   Report all family/class counts and the confusion matrix, including failed
   no-action cases that predict an action. A majority-class shift is not learning.

Controls are fixed before model evaluation: constant 0/1/2/3, training-majority,
primitive negation (`nicht|kein*|nichts|niemals|nie` => 0; else 3), first and
last task keyword, training-only word unigram/bigram and character 2–4-gram
multinomial naive Bayes (Laplace alpha 1), and training-only nearest-centroid
classification using UTF-8 bytes, characters, word count, and native token
count. No hyperparameter search or holdout-fitted vocabulary/threshold.
Controls are diagnostic; no heuristic is placed in front of LM1.

Before training, include the data pilot's fixed stronger controls too:
word-unigram count logistic regression, word 1–2-gram TF-IDF logistic
regression, character 3–5-gram TF-IDF logistic regression (all C=1,
max_iter=1000, random_state=20261111), and a depth-three decision tree on
lengths plus count of `nicht`. Fit only the same frozen TRAIN. The exact
legacy substring-`nicht` rule and negation-parity rule are reported separately.
`controls.py` fixes lexical action stems before any holdout/model evaluation.

If any trivial control solves at least 90% of the independent semantic
holdout, the benchmark is shortcut-compromised and cannot support adoption.
Do not quietly edit that frozen holdout after seeing model results.

## Retention and collapse gates

Historical open/search restricted accuracy may decline by at most 5 percentage
points, and at least 90% of the originally correct individual cases must remain
correct. Report per-class recall: the historical original log shows only 4/32
open and 28/32 search, with predictions 8 open / 56 search. The aggregate 32/64
equals a balanced majority baseline; it is a fragile behavior, not strong
previous language understanding. Preserve it without exaggerating its value.

On historical and novel text sets separately, aggregate bits per UTF-8 byte
must rise by no more than 5%. On continuation probes, median generated length
must remain at least 90% of original, the number of probes ending within the
first two tokens may not increase by more than one, and all originally
32-token historical probes must still reach 32 tokens. Report text samples
and repetition diagnostics; these conditions only detect gross degradation.
Historical original lengths were 7/4/32/32, so an all-four-long prerequisite
would be invalid. Report first-token EOS rates for classification separately;
an increase exceeding 5 percentage points is a collapse veto.

## Integrity, reproducibility, and limits

Bind exact checkpoint bytes, tensor-only hashes, parameter counts, tokenizer,
native evaluator sources, build inputs, seeds, all datasets, actual commands,
stdout/stderr, process exit codes, time, and available memory/CPU measurements.
Evaluate only finite valid checkpoints; metadata changes alone never establish
weight training. Preserve all old artifacts and record their initial hashes.
One inference repeat of original and each new frozen checkpoint must produce
identical per-case token/probability outputs. This is repeat inference, not an
extra training trajectory. Training-state continuation evidence is checked
separately and must not consume a third candidate budget.

Wilson intervals describe row-level proportions with a dependence warning;
paired bootstrap effects are primary. No confidence interval turns synthetic
template transfer into proof of general intelligence. No device, APK, Gemma,
signing, memory, avatar, or Cinema modification is authorized by passing this
research protocol. A gate failure means REJECT FOR PROMOTION, not a hidden
round of optimization. Final decision belongs to the coordinator.

## Initial status

Initial protocol commit: holdout creation PENDING; native evaluator NOT_RUN;
A/B NOT_RUN. Subsequent preflight receipts record data creation and tokenizer
validation without model inference. Training/data integrity audit PENDING.
No success claim.
