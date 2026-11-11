# LM1 semantic training handoff

Status: PREPARED / COMPILED / NOT_TRAINED. EXP-05 / EXP-12, research only.

Basis: `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`, PR #728.
Branch: `research/continuum-lm1-semantic-training-20261111`, draft PR #750.

## Implemented and measured

New native harness reuses unchanged pinned LM1 sources and isolated historical
optimizer/state patch. Exactly two prospective candidates, A and B, maximum
128 actual updates each; no architecture, app, model-choice or release change.
Complete source/data/command freeze is `training/registration.json` beneath
`experimental/lm1-semantic-20261111`. All new work stays in this subtree.

Source review: historical 32/64 open/search is restricted classification only,
with 56 search / 8 open predictions (28 / 4 correct). It equals balanced chance.
Original free exact open/search is 0/64; retaining 0 would conceal forgetting.
Historical generation lengths 7 / 4 / 32 / 32 measure stopping, not coherent language.

Zero-update native preparation verified 32/64 new training-only retention
anchors (8 open, 24 search correct), and selected 8 + 8 correct. Twelve of 16
independent training text prefixes reach at least 24 tokens; first 8 selected.
Generated sequences include repetition and do not prove coherent language.
No evaluation or holdout case constructs a training target.

All 192 training BOS/prefix arrays match the independent Python tokenizer.
Kotlin 2.4.0 compiler/stdlib hashes match the old experimental toolchain.
Native compile exit 0; twelve finite-difference coordinates validate mixed
label/EOS and sliced rollout-KL gradients (maximum error 0.00002538).
All fifteen existing KL diagnostic checks pass; zero model updates.

## Next execution

Root must publish sources, data and independent evaluation protocol, then
create the hash-bound gate required by `training/run_semantic.py`.
Run candidate A once, save/commit/publish its full evidence, then B once.
The runner refuses repeated execution via an exclusive per-arm ledger.
The native sequence is 64 updates + byte-identical state roundtrip + 64 updates;
no hidden training duplicate. Early-stop criteria and resource limits are
fixed in the registration, and no failed run is restarted.

Candidate B protects actually verified training label rankings and original
greedy generation trajectories; it does not reuse sixteen unrelated replay
statements as its preservation objective. Teacher arrays are independently
decoded and immutable; cached distributions are hashed before/after.

## Limits and recovery

No new candidate has trained. Independent evaluation and application/phone
measurements remain NOT_RUN. Holdout contents/labels remain unread.
All historical files and original input weights remain unchanged. Rollback:
omit this isolated research branch. No automatic model promotion.
