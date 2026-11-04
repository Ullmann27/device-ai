# Ten-bot support team — 2026-11-04

## Mandate and source

Support the four active Continuum development workstreams with at least ten
separately assigned assistant agents. Reuse existing code, tests and execution
contracts. A development assistant in this session is not an installed Android
bot or an independently running local model.

- Repository: `Ullmann27/device-ai`, owner-authorized public collaboration.
- Base: PR #483, `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`.
- Branch: `support/continuum-ten-bot-assist-20261104`.
- Initial checkpoint: coordination only; implementation and new tests NOT_RUN.
- Capacity: three concurrent helper agents plus the coordinator; dispatch in waves.

## Protected workstreams

| Workstream | Observed branch / PR | Ownership |
| --- | --- | --- |
| Model weights | `research/continuum-weight-space-audit-20261104`, #492 | Model tensors, capacity experiments, training and promotion |
| Brain and retrieval | `feat/continuum-memory-brain-20261104` | Cognitive retrieval, memory, reasoning and event bridge |
| Avatar and design | `codex/astra-jarvis-takeover-20261104`, #491 | Avatar/orb, shared Activity presentation, visual integration and recovery coordination |
| Personality bots | `feat/continuum-personality-bots-20261104`, #493 | Bot profiles, addressing, dialogue, assignment hooks and roster UI |

Observed heads are a point-in-time inventory, not an acknowledgement from those
chats. Re-read their current status before integration. Never write to their
worktrees, branches or unfinished files. Shared `AgentActivity.kt`, cognitive
stores, bot profiles and avatar renderers stay with their current owners.

## Dispatch ledger

| Bot | Assignment | Initial state |
| --- | --- | --- |
| 01 | Map current owners, branches and file conflicts | RUNNING, read-only |
| 02 | Verify available local build and test tools | RUNNING, read-only |
| 03 | Audit existing local worker and scheduler for reproducible gaps | RUNNING, read-only |
| 04 | Test worker cancellation, duplicate jobs and safe execution | QUEUED |
| 05 | Check model artifact and evidence contracts for the weights team | QUEUED |
| 06 | Check brain/retrieval integration boundaries and evidence | QUEUED |
| 07 | Check avatar/design integration and asset contracts | QUEUED |
| 08 | Check update identity, source binding and version monotonicity | QUEUED |
| 09 | Check app-bot assignments and permissions against existing gates | QUEUED |
| 10 | Independently review fixes, regressions and integration conflicts | QUEUED |

Later entries will record actual dispatch, exact paths, results and source
commits. Queued roles are not counted as agents that already ran.

## Execution and evidence rules

Relevant experiment contracts: EXP-00/01/02/05/06/07/08/11/12, according to each
actual change. Root AGENTS.md, the experiment contract/register, Issue #41 and
current-state/frontier references were read before implementation. Historical
release-freeze milestones are not current release acceptance.

Use independent worktrees or exclusive file ownership. First reproduce a
concrete defect, then apply a bounded fix and verify the behavior. Use the existing
worker, scheduler, backlog, local runtime and ToolGate/CCC1 paths. Do not introduce
a parallel service or backdoor. New tests must exercise relevant behavior.

Serialize Gradle through the existing shared build lock. Preserve original logs
and distinguish local host tests from Android device, native model and release
results. Do not run GitHub Actions, paid services or the native S01 replay.

No main merge, force push, branch deletion, signing, release, installation,
permission expansion, private data publication or model promotion is included.
The package, original signer and existing update channel remain owner-controlled.

## Continuation and rollback

Read the latest commit and this ledger; verify branch ancestry, claimed paths and
all exact-source evidence. Integrate only reviewed support commits into the
designated review branch. Revert this team's commits individually if needed;
never reset or overwrite another workstream. Fold7/APK/signing remain NOT_RUN
unless a later entry includes the actual independent evidence.
