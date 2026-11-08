# Continuum Bot Universe - first vertical slice

Branch: `astra/continuum-bot-universe-20261108`
Base: SOL #658, remote checked at `f153758dfd19d1c12f6fe63b6c4c4bd1da95e4cb`.
Status: WIP / NOT_RUN. No Android/device acceptance, merge, signing or release.

## Inventory and integration boundary

- `ChiefBotProfileStore` already preserves the 20 stable IDs, owner edits,
  isolated working notes and conversations. Profiles grant no tools or skills.
- `ChiefBotConversation` uses the shared local runtime with private context.
- `ChiefBotTaskIntake` connects explicit single-bot assignments to the owner
  execution lane; its ticket and UI status currently disappear on restart.
- `ChiefDelegationCoordinator` and `ChiefWorkerPool` provide bounded delegation,
  but are not yet connected to a persistent owner-selected team.
- `ChiefJobs` reuses `BoundedAgentOrchestrator` and `ToolGate`.
- `ChiefBotHandoff` / `ChiefWorkspaceWiring` bind coding work to CCC1 authority.
  Their job IDs and grants must not be fabricated for ordinary bot conversations.
- Existing roster/task dialogs already share JARVIS typography, palette and
  responsive columns. No new navigation stack, avatar or rendering loop needed.

## Bounded implementation

EXP-11 (existing specialist orchestration), EXP-12 (restart continuity).
First owner-approved job: analyze the supplied text, then critically review
that result with a different selected bot. Use the existing coordinator/pool
and orchestrator, two serial calls to the shared local model, no tools,
network, private conversation sharing, memory promotion or training.
The initial decomposition is an explicit two-stage template, not a claim of
general autonomous planning. Runtime capability is tool-free text generation,
not inferred professional competence from a profile name.

Persist chief/team selection and bounded task records in the existing bot
store; preserve existing profile/conversation schemas. Stable request identity,
bound profiles/model/attempt, cancellation, stale-result refusal and interrupted
records without automatic re-execution. Coding jobs remain exclusively CCC1.
Model execution receipts prove generation, NOT factual correctness or tool
success. UI must preserve that distinction.

## Validation and resume

Next: implement the durable records and existing coordinator adapter, then
wire roster/team/task UI. Test chief changes, duplicates, missing model/consent,
restart, cancellation, stale profile/model/result and invalid handoff.
Host Kotlin/JUnit available in session artifacts. Android SDK/adb unavailable
at entry; do not inherit SOL's test results as evidence for new source.

Rollback: revert this branch's changes as a unit after preserving task data.
Never delete or rewrite existing profiles, conversations or CCC1 receipts.
