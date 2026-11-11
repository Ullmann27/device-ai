# LM1 semantic training handoff

Status: WIP / NOT_RUN. EXP-05 / EXP-12, research only.

Basis: `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`, PR #728.
Branch: `research/continuum-lm1-semantic-training-20261111`.

## Scope and state

Exactly two prospective candidates: balanced semantic training A and the same
training with targeted verified capability preservation B. Each is limited to
128 updates, from unchanged original 132,160-parameter weights. No model update
has run. Data pilot and root protocol gates must pass first. Holdout contents
and labels must not be read for training decisions.

Historical file hashes are recorded in `experimental/lm1-semantic-20261111/training/start-manifest.json`.
The original restricted open/search result is 32/64; original free exact
open/search is 0/64. Preserving the latter alone would hide a floor effect.
Original generation lengths are 7/4/32/32; teacher rehearsal on sixteen
statements previously failed both observed 32-token successes.

## Verification and next entry

Read-only checkout verification: exact requested base and isolated branch.
Java available; Kotlin compiler path and original input paths are pending.
No compilation, new-model training, independent evaluation or APK run yet.
Next: verify original bytes, native trainer and training-state serialization;
prepare a targeted preservation objective and prospective execution manifest.
Wait for root's explicit internal data/protocol authorization before training.

## Safety and rollback

Only new research files and this handoff may change. Prior sources, artifacts
and failed results remain unchanged. No main/build/model/owner-memory/signing
change. Rollback is omission of this research branch; original weights stay
authoritative. Never promote these candidates automatically.
