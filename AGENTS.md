# Continuum Codex agent instructions

Repository: `Ullmann27/device-ai`. Objective: evolve an Android-first, locally capable personal AI assistant while preserving existing release and owner safety boundaries.

This repository contains active experimental PR stacks. Treat a GitHub branch, PR, source-only test, host test, Android build, and Fold7 device result as distinct proof levels. Do not claim that draft PR code is in `main`.

## Operating contract

1. Before editing, inspect the current checkout SHA, branch, relevant current PRs and source paths. Follow `docs/codex-team/TEAM-CHARTER.md` and the assigned lane prompt in `docs/codex-team/STARTPROMPTS.md`.
2. Remain on your assigned `codex/team-*/...` branch, or create a new branch within that lane from a pinned base. Never commit or push directly to `main` or release/production branches. Never force-push, reset shared branches, or silently merge a PR.
3. Implement only the assigned lane's changes. Any cross-lane edit requires a documented request to Chief; Chief grants an exclusive change window with a named owner. Read-only inspection of all lanes is permitted.
4. Work in evidence-based cycles of **up to 30 meaningful steps**. Do not invent filler actions to reach 30. At each checkpoint, re-read other lanes' current commit SHAs, open PRs, cross-lane contracts, and the architecture overview. Record what actually happened using `docs/codex-team/CHECKPOINT.md`. If context or available execution ends early, create a checkpoint with the completed count.
5. Tests are a gate. Record the exact command, environment, checkout SHA and pass/fail result. If Android SDK, emulator, local model, Fold7 or tool approval is unavailable, report that limitation explicitly and run only the checks that are possible. No fabricated output.
6. Never modify private owner data, secrets, credentials, signing keys, application ID/versionCode, original Render publisher, production releases or actual device state without separately authorized and verified procedures.
7. Run only task-necessary commands in the assigned sandbox. Respect the existing CCC1 repair/ToolGate approval chain; do not use arbitrary model-generated shell commands to bypass it.
8. Preserve independent verification: a code author cannot approve their own acceptance proof. Security failures or source drift block integration; do not weaken tests to produce a green status.
9. At the end of every task, leave a commit or draft PR when permitted, with concise evidence and a next-cycle handoff. If no code was changed, say so.

A branch name, file or prompt does NOT instantiate a running agent. The Codex Cloud environment and tasks must actually be created and started in Codex; see `docs/codex-team/TEAM-CHARTER.md`.
