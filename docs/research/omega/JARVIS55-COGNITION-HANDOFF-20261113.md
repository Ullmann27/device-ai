# Code55: cancellation and speech contracts

Source base: `8a882e662cb48e3363213eb5005cb091828d89ec` (#811), based on
#809 `779863955825b65931ef569dde79a6d2f8f8c771`.
Integration target: `work/jarvis55-master-20261113`, #812, then #809.
Relevant contracts: EXP-07, EXP-11, EXP-12. No model weights, memory promotion,
tool authority, render pipeline or persistent schema changed.

## Actual on-device team capability

`AgentActivity.showBotRoster` constructs the existing `ChiefBotTeam` against the
shared local runtime. The profile store seeds 20 identities; identities are not
separate loaded models or evidence of competence. An explicitly selected chief
and two specialists use `ChiefDelegationCoordinator` and `ChiefWorkerPool(1, 1)`
for a sequential ANALYZE then REVIEW pass. Both specialist tool allowlists are
empty. `ChiefJobs` retains the existing bounded orchestrator and ToolGate.

The model must be READY with a bound SHA. Each stage gets one model call; task,
team revision, profile versions, hashes and cancellation are checked again after
generation. The second model response is explicitly not an independent test.
Initialization marks saved active jobs INTERRUPTED; duplicate task identities do
not execute again. This audit does not establish which agents are enabled on the
owner's phone. Development agents in this session are separate from these app
profiles.

## Production repair

Previously `ChiefBotTeam.stop` wrote CANCELLED before cancelling the running job.
An unreadable/corrupted team-state file threw before cancellation, so a direct
STOP could leave model work running. The fixed method invalidates admission and
cancels first, then attempts the state write. Storage failure remains visible as
`BOT_TEAM_RESULT_NOT_COMMITTED` and is rethrown; no persisted success is invented.
Late responses still fail the existing cancellation/current-task checks.

The regression corrupts the actual team file while a real worker-pool assignment
is executing a controlled model port. It verifies token cancellation, queued
admission revocation, one model invocation and no accepted late response. The
test is not a real local-model inference or Fold7 test.

## Full-QA speech failures

The failed parent QA expected a listening indicator immediately after requesting
ASR. `LocalSpeechSession` now deliberately sets that indicator only from the
actual ready callback. The ResearchChatApprovalUiTest fixture now asserts OFF,
delivers `onReadyForSpeech`, then asserts ON. Approval/session/replacement
assertions remain intact.

The silence regression expected only one retry although the current bounded
policy allows exactly three. It now checks all three, cold recognizer progression,
the fourth failure pausing, automatic foreground recovery staying PAUSED, and
explicit owner-tap resumption. Service errors still allow only one retry.

## Verification

- Pure speech baseline: 37 tests, 1 failure at the obsolete silence assertion.
- Revised speech tests: 37 tests, 0 failures.
- Team STOP baseline: 39 tests, 1 failure (cancellation missing).
- Team candidate: 39 tests, 0 failures across team, coordinator and worker pool.
- Host uses Kotlin 2.0.20 with metadata compatibility and inline-break flags;
  the canonical Kotlin 2.4.0 Android build is still required. No production stubs.
- Raw logs and source/dependency hashes: `docs/evidence/jarvis55-cognition-20261113/`.
- ResearchChatApprovalUiTest Android execution: NOT_RUN locally.
- `git diff --check`: PASS.
- Android/Fold7/model-quality/thermal proof: NOT_RUN.

## Integration and rollback

Review the STOP ordering and run ChiefBotTeamTest, ChiefBotTeamSkillsTest,
ChiefDelegationCoordinatorTest, ChiefWorkerPoolTest and ChiefWorkerPoolShutdownTest
with the canonical Android toolchain. Then run ResearchChatApprovalUiTest and
the complete QA suite on the combined source. Do not weaken the gates or count
the earlier parent run as candidate evidence. Roll back only this lane's commit;
no data migration or model restore is needed.

Follow-up finding: the bounded orchestrator currently labels accepted text-only
answers BLOCKED when no action outcome is verified. A later typed presentation
refinement should distinguish answer availability from action verification,
without treating model text as a successful device action.
