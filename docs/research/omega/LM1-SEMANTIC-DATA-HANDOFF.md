# LM1 semantic data handoff

Status: WIP_UNVERIFIED. Experiment EXP-05 / EXP-12, research only.
Base: `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667` (PR #728).
Branch: `research/continuum-lm1-semantic-data-20261111`.

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

## Next step

Read and inventory the old data sources and measured audits. Author a small
balanced, contrasting TRAIN/DEV pilot with distinct families and objects.
Run tokenizer and shortcut audits before training authorization. All actual
results and file identities will replace this WIP status in a later commit.

## Checks and rollback

At start, branch and base HEAD verified. New data/tests NOT_RUN.
Compatibility: unchanged label IDs, tokenizer and prompt wrapper.
Rollback: exclude this research directory; no app path depends on it.
