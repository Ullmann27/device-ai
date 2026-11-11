# Personal voice independent evaluation — 11 November 2026

Status: registration in progress, no new model results inspected.
Research base PR #728 / `1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667`.
Bot 3 owns only `experimental/personal-voice-20261111/evaluation/` and this handoff.

The current owner instruction authorizes one isolated personal-voice round with
exactly three helpers, at most two native LM1 candidates, public synthetic text
and no production integration. Existing AGENTS/experiment contracts were read;
EXP-05/EXP-12 apply, Fold7/ASR/device acceptance remains separate.

Initial source evidence confirms the three historical candidates failed gates,
labels 0/1/2/3, original LM1 and tokenizer hashes. Freeze protocol and independent
64-case TEST before new candidate results; no TRAIN/DEV examples have been read
by this evaluator. The shared-worktree access rule is a process control, not an
encryption claim. No private transcripts or owner recordings were collected.

Next: publish immutable freeze hashes, verify LM1 token length, run the independent
native evaluator once per original/candidate after both candidate hashes freeze,
and evaluate root's native correction adapter separately. Full A/B/C application
and actual WER/CER are NOT_RUN until their real inputs/runtime are available.

Rollback: omit isolated evaluation directory. No app, model selection, checkpoint,
Gemma, main, signing, release or existing evidence was changed.
