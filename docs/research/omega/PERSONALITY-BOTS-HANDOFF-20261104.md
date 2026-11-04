# Continuum personality bots — 2026-11-04

## Scope and source

Owner request: implement callable bots inside the existing Android app, with
distinct personalities, useful task understanding and access through the current
cyan/violet Jarvis design. Continue autonomously and coordinate with the other
active development conversations.

- Base: PR #483, `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`.
- Branch: `feat/continuum-personality-bots-20261104`.
- Initial status: **WIP / new implementation NOT_RUN / APK NOT_RUN**.
- Actual current code already contains 20 persisted profiles, private local bot
  dialogue, address routing, ChiefWorkerPool, typed delegation and CCC1 contracts.
  A private bot dialogue currently cannot dispatch tools. A profile is neither a
  verified skill nor an independently running model.

## Coordination

This branch owns bot profiles/personality presentation, bot addressing, bot task
entry and their focused tests. It reuses ChiefBotProfileStore,
ChiefBotConversation, the existing local model lane and the existing owner task
paths. It does not create a competing memory database, worker pool, model runtime
or repair pipeline.

Other current workstreams:

- #491 / `codex/astra-jarvis-takeover-20261104`: visual integration, avatar/orb,
  AgentActivity presentation and M2.10 recovery safety.
- `feat/continuum-memory-brain-20261104`: cognitive retrieval, evidence/reasoning
  and memory-event integration.
- #492 / `research/continuum-weight-space-audit-20261104`: weights and capacity
  experiments.

Shared-file policy: any unavoidable AgentActivity edit is confined to the bot
selection/dialogue/assignment methods and imports/fields they need. No wholesale
Activity replacement, avatar geometry, speech renderer, model weights, cognitive
retriever or recovery-store changes. Compare exact heads before combined
integration. This written coordination is not an acknowledgement by another chat.

## Design choices

Build on Kotlin/Android Views and the existing Jarvis colors/components. Expose
each bot's name, role, personality and actual availability, plus direct chat and
assignment actions. Preserve edited profiles and private per-bot context. A
personality changes wording and working style; it never grants capabilities.

Task understanding must preserve the owner's complete request and constraints.
Read-only/local dialogue can use the existing private bot lane. Any effectful
task must enter the existing owner-reviewed dispatcher and ToolGate/CCC1 path;
model text, a bot name or a suggested task cannot itself grant execution.
Show real progress/results only; unavailable execution is visibly unavailable.

Alternatives considered: a second multi-agent framework would duplicate existing
gates and state; cosmetic profile cards alone would leave assignment disconnected.
The selected implementation extends the existing profile/dialogue/task paths.

## Verification and constraints

Relevant contracts read: AGENTS.md, experiment contract/register, current Issue
#41, current-state/frontier handoff. EXP-05, EXP-06, EXP-07, EXP-11 and EXP-12 apply.

Use focused regression/negative tests, then relevant integration/UI tests,
Android compilation and configured lint on the exact source. Coordinate Gradle
through `/workspace/scratch/b3170a7ae924/gradle-build.lock`. Local host tests are
not Fold7, physical GPU, native model or speech acceptance.

No main merge, force push, paid API/runner, model promotion, permission expansion,
S01 replay, sign/release/version changes or installation. Preserve package
`at.heinz.deviceai.continuumlocal`, the original signer and current update path.
No private owner inputs or credentials in Git.

## Execution ledger

1. Source / app-path audit: IN_PROGRESS (three read-only specialists).
2. Baseline: NOT_RUN in this branch.
3. Personality and callable addressing: PENDING.
4. Assignment connection and status: PENDING.
5. Modern roster and UI acceptance: PENDING.
6. Independent review / integration handoff: PENDING.

Revert only this branch's new commits to roll back. Do not reset shared branches.
