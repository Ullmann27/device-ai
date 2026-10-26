# Coding/discovery continuation — 2026-10-26

Status: `WIP / UNVERIFIED`. Initial checkpoint before source changes.

Base: `c391b832` on `sol/continuum-brain-breakthroughs-20261026`, descendant of
PR #150 (`ad14f8b82f9be2cd0026bc16eef71a16bec41424`). Working branch:
`sol/coding-discovery-safety-20261026`, intended draft base is the parent branch.

## Scope and contract

EXP-00/01/02/05/08/09/11: inspect existing mobile-builder, owned-app, typed coding,
CCC1, canonical backlog and repair pipeline. Find the smallest proven missing
connection or fail-open, reproduce it with a red test, then repair it. Persistent
retry/diagnostic reuse must use the existing job and repair state, with bounded
attempts. No second backlog, framework or Android main-app replacement.

Read: root AGENTS.md, experiment contract/register, current integration register,
copilot instructions, active checkpoint, Opus night handoff, and live Issue #41
(updated 2026-10-22). The owner phone remains the primary development machine;
this optional Linux host cannot establish Fold7/Termux end-to-end acceptance.

## Safety and rollback

- No model-text shell and no arbitrary unisolated executable candidate.
- A git worktree is source isolation, not an OS sandbox. Real executor capability
  must be checked before any candidate execution; unavailable isolation blocks.
- No main merge, owner signing, installation, model training or promotion.
- Changes remain review-only on this branch. Rollback: revert branch commits;
  canonical prior state remains on the parent integration branch.

## Checks and next entry point

Completed read-only: repository HEAD/ancestry/remotes, clean starting worktree,
live Issue #41 and current managed environment status. Managed network policy
is enforced/unrestricted. No credentials printed.

`NOT_RUN`: coding tests, Node suite, SDK build, model calls, candidate execution,
Fold7/device tests. Next: map actual entry points and executor capability, run
lightweight baseline tests, then archive exact red/green evidence here.
