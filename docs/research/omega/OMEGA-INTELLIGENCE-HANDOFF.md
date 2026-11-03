# Operation Continuum Ω — Intelligence lane, 2026-11-03

Status: WIP / UNVERIFIED. No device/model-quality claim.

## Source and authority

- Repository: `Ullmann27/device-ai`.
- Branch: `codex/team-01-intelligence/omega-20261103`.
- Baseline: PR #390, `b4f61ae9765e26dd401db928b76fa9f233c0742a`.
- Draft target: `fix/continuum-code40-router-race-review-20261103`.
- Current Operation Continuum Ω owner instruction authorizes isolated work, tests,
  push and draft PR; historical v31 research freeze is superseded. No main merge,
  release, signing, installation, weights, actual owner data or persistence change.
- Read AGENTS.md, experiment contract/register, current-state/frontier handoffs,
  chief handoff and current GitHub issue #41. Relevant EXP-05/06/07/11/12 interfaces
  remain unchanged; this is a deterministic context fix, not a new experiment
  activation or model capability claim.

## Exclusive scope and plan

- Production: `app/src/main/java/at/heinz/deviceai/cognitive/OwnerStatementTimeline.kt`.
- Tests: `app/src/test/java/at/heinz/deviceai/cognitive/OwnerStatementTimelineTest.kt`.
- This handoff and `docs/evidence/omega-intelligence-20261103/`.
- Trace actual context/authority flow, freeze a meaningful negative regression,
  observe RED, minimally repair the same projection, run focused and available
  integration tests, record exact source and logs, push each meaningful checkpoint.
- Candidate root cause under investigation: entity segments are clipped to 80
  characters and only pre-number negation/hedging is checked. A negated quantity
  after the selected number can become an affirmative current count.

## Actual call graph and limits

`AgentActivity.runLocal` composes `LocalFollowUpTaskFrame` from complete owner-only
session evidence, then uses `BoundedAgentOrchestrator`. Its model callback invokes
`AiContextCompiler`, renders `LocalConversationContext`, checks
`LocalConversationFollowUpPolicy`, and conditionally adds
`OwnerStatementTimeline.project(...).render()` to the required prompt body.
`LocalPromptFrame` and `LocalContextIntegrityProbe` enforce complete required
history. Original user text remains the authority source for writes/tools.

`LocalConversationContext` stores at most four ephemeral turns. Owner literals
over 1,000 characters make evidence incomplete; model replies/research are not
owner evidence. `LocalContextRetentionPolicy` keeps read-only context and drops it
after memory changes; safety refusals are not retained as task data.
`AiContextCompiler` admits whole sections under a byte budget, filters stale and
contradicted memory, and marks external research untrusted. The timeline is a
bounded German quantity parser, not semantic reasoning, persistence or proof.
No alternate cognitive pipeline is introduced.

## Existing evidence and limitations

The predecessor Argon-4 dossier reports 8 isolated timeline assertions and 16
storage-negation assertions, explicitly not Android or the new JUnit suite.
Code39 field observations motivated the ring follow-up frame; those reports are
not a benchmark of this branch. Historical Gemma host results and synthetic
compute-reuse measurements do not validate this change. No model weights,
Gemma inference, Android device or performance experiment runs in this lane.

## Verification / continuation

- Baseline source inspection: completed at the SHA above.
- Focused new regression: NOT_RUN; test harness coordinated with Quality lane.
- Android/Gradle/full suite: NOT_RUN; SDK readiness owned by Quality lane.
- Next: freeze and run the smallest suffix-negation/correction reproduction.
- Rollback: revert only this lane's source/test commit; no migration or data
  rollback required. Preserve all logs and negative results in this branch.
