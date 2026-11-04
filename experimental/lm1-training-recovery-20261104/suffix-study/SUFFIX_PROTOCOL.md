# LM1 intent study v3: short prompt suffix

Prospectively registered on 2026-11-04 before this round's training or any final
test evaluation. Scope remains `RESEARCH_SHADOW`.

## Evidence motivating this single change

The completed v2 long study, source
`fc2eb797210bee25091caf734aacbfd694a3372e`, selected small step 256 at 13/24 Dev
exact and wide step 128 at 13/24. Wide subsequently fell to 11/24 at steps 256
and 512 while training minibatch losses became small. This establishes a
development gap, not a diagnosis of its cause. V2 has a valid development
candidate; its final test is explicitly deferred for one more prospectively
declared development comparison. Its original rules and evidence are unchanged.

The independent `../suffix_probe.py` calculation at the original checkpoint
measured the direct layer-0 state-difference retention across `\nAktion:`.
Its median was about 0.00343, compared with about 0.1900 for a single newline.
This is neither the full network Jacobian nor a measurement of trained models.
It motivates testing the entire shorter wrapper, without predicting success.

## Controlled intervention

The sole functional change from `../long-study/LmIntentLongStudy.kt` is:

```text
v2 input: Auftrag: <prompt>\nAktion:
v3 input: Auftrag: <prompt>\n
```

Class/experiment names change for isolation. Checkpoint identity therefore also
includes a different experiment name. The model architecture, original weights,
tokenizer, width-8 transformation, seeds, TSV files, example order, labels,
answer/EOS mask, optimizer, peak learning rate, warmup, cosine schedule, clipping,
workers, snapshots and selection rules are unchanged from the long protocol.
Both model sizes start fresh from the hash-bound original; no trained checkpoint
is resumed. The unchanged initial capacity gate must pass before any update.

Each arm uses 512 updates, batch 4, sixteen passes through the same 128 training
examples and 4,096 supervised targets. Snapshots are 0/128/256/512; only the three
trained snapshots are selection candidates. Limits remain 3 GiB / 900 seconds for
each parent,840 seconds internally, and 1 GiB / 120 seconds for each capacity child.

The example schedule hash must equal v2. The token schedule hash must differ:
four suffix input tokens are removed per example, and both v3 sizes must agree
on their new token schedule. The shorter sequence also costs less compute.
Any advantage concerns the combined wrapper change: shorter recurrence path,
removal of the word “Aktion”, and a changed last input token. It does not isolate
forget-gate damping as the cause. All four v3 controls use the short wrapper,
including the untrained S0/W0 comparisons.

## Development selection before the one final test

Within each study and size, choose by Dev exact descending, answer NLL ascending,
then fewer steps. A study is eligible only after complete, valid runs and the
existing wide Dev gate: at least 12/24 and at least four more than its W0.

Before starting v3, the rule for choosing between the two completed study
versions is fixed as follows: compare their selected W1 checkpoints by Dev exact
descending, then answer NLL ascending; on an exact tie retain v2. Use the complete
four-arm set belonging to that chosen version. Do not choose a small control
from a different wrapper version. Record rejected/ineligible versions and all
development results. Repeated Dev use remains development, not confirmation.

`choose_development_study.py` validates the registered sources, complete training
receipts, initial gates and both candidate freeze payloads. It refuses any
already-created final-test log, binds the common test hash, and writes a new
choice receipt. The coordinator then freezes only the selected version and
verifies that its freeze payload exactly matches the published choice. Publish
the choice and freeze before evaluating the final test once on S0/W0/S1/W1.
If neither version is eligible, record failure and keep the test unevaluated.

This is the last development variant in this round. After the final test, no
wrapper choice, retraining or new selection will be justified by that same test.
Its outcome is accepted and reported, including failure. Follow-up development
would require a new independently specified confirmation set.

After the final choice and test, the selected S1/W1 checkpoints will also be
scored on all 128 known training examples using the same free-generation scorer.
This is a descriptive fixed-checkpoint fit audit, with the TRAIN file hash
reported explicitly; it does not select a model or replace any final-test score.
Each audit process retains the 900-second bound. Training-set accuracy is not
independent generalization evidence. It distinguishes memorized training
behavior from transfer to the different development and test templates.

## Unchanged acceptance, boundaries and rollback

The limited task criterion remains W1 at least 36/48 exact free digit-plus-EOS
answers, at least 8/12 per class, and at least eight more than each initial
control. A selected wider-model advantage additionally needs at least four more
correct than S1; different selected step counts must be disclosed. All 48 cases
are reported; template-family dependence precludes treating them as 48 independent
draws. No general-intelligence or open-ended action capability follows.

The unchanged original capacity benchmark is repeated for selected trained
models. Its 32-token requirement is never replaced by the one-digit task. A
capacity failure remains explicit and prevents general-model suitability or
promotion. No productive training, runtime change, Android installation, main
merge or release is authorized by this study. No foreign pretrained weights or
paid inference service is used. Keep the original model to roll back; all new
weights are isolated research artifacts.
