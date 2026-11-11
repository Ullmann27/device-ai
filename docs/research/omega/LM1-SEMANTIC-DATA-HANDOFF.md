# LM1 semantic data handoff

Status: DATA_AUDIT_COMPLETE; awaiting coordinator training gate.
Experiment EXP-05 / EXP-12, research only.
Base: `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667` (PR #728).
Branch: `research/continuum-lm1-semantic-data-20261111`.

## Confirmed remote publication

Draft PR: https://github.com/Ullmann27/device-ai/pull/749

Start checkpoint: local `23872f1a949b2cfc9eb776beb8290fa61b6fd795` maps to
published `d7bd614cbf273d74b246f876f33f30860cbd5696`.
Completed data checkpoint: local `193faa4c21bc2340e4c4cce5fa922513006b10b8`
maps to published `d7b948830607a087d84b6f492143354ab28d14a5`.
The coordinator verified identical Git trees for each local/remote pair.
Direct Git authentication was unavailable; publication used the authorized
GitHub connector without force-push or any protected-branch change.

## Scope

New files only under `experimental/lm1-semantic-20261111/data/` and this handoff.
Bot 1 owns TRAIN/DEV authoring, tokenizer diagnostics and trivial baselines.
Bot 2 owns bounded native training. Bot 3 owns an independent held-out set.
This bot must not access the holdout before candidate weights are frozen.
No additional agents, model training, production edits or artifact promotion.

## Source findings

The old generator makes every positive directive start with Bitte/Jetzt and
every negative directive with Nicht. This is an exact lexical shortcut.
Its byte-matched prompts do not ensure token-matched prompts. Label mapping
is 0=no action, 1=open, 2=search, 3=note. The prior 32/64 open/search result
was restricted classification; baseline exact generation was already zero.

## Completed data checkpoint

`experimental/lm1-semantic-20261111/data/README.md` is the substantive report.
The generator created 192 TRAIN and 96 DEV rows over four equal classes,
96/48 opposing pairs, four construction families per split and distinct names.
Every pair has equal word counts, UTF-8 bytes and native BPE token length.
Strongest declared shortcut: word-bigram logistic, DEV49/96 (51.04%), below
the preregistered90% pilot rejection threshold. First/last-action controls
achieve24/48 complete DEV pairs, a required comparator for neural claims.

Data identities: TRAIN `de702b80266c6046a2414a267991379367aebd28586f6e59d6bff02ceeca2a5b`;
DEV `ae9da87ab5a4622918d81d94ce2effc9454f0646c3793b67aed9cddc3d179db7`.
Native Kotlin2.4.0 independently verified all288 input token arrays,
round trips, label tokens and context limits. Max prefix96/104 tokens.
Eight Python checks pass. Thirty-four historical data files are byte-identical
to the verified base. An earlier Kotlin2.0.20 tokenizer pass is separately
preserved; it is not the data-release compiler gate.

Bot1 performed zero LM1 model inferences or training updates. Holdout contents
were not accessed. Raw control predictions, token arrays, file hashes, native
logs/receipts and test receipts are committed. No production path was changed.

## Next step

Coordinator reviews DATA-FREEZE.json and authorizes the two bounded candidates
only if independent protocol and training implementation gates also pass.
Bot2 consumes the frozen TRAIN rows; DEV remains openly inspected diagnostics.
Bot3 holds an independently authored confirmation set. Do not tune these
sources against any future holdout result or enlarge the experiment.
After the coordinator explicitly confirms candidate freeze, Bot1 may inspect
the holdout solely for an independent semantic-label audit and final raw-result
comparison. Before that explicit handoff the holdout remains unread. Any
ambiguity found afterward must be reported, not silently corrected or removed.

## Checks and rollback

Branch and base verified; source inventory, data audit and native checks PASS.
Compatibility: unchanged label IDs, tokenizer and prompt wrapper.
Rollback: exclude this research directory; no app path depends on it.
