# Operation Continuum Ω — Intelligence lane, 2026-11-03

Status: SOURCE_INTEGRATED / focused host JVM verified; full Android gate pending.
Draft PR: https://github.com/Ullmann27/device-ai/pull/394. No device/model-quality claim.

## Source and authority

- Repository: `Ullmann27/device-ai`.
- Branch: `codex/team-01-intelligence-omega-20261103`. Original nested name was
  rejected by GitHub because `codex/team-01-intelligence` already exists; that
  existing branch is untouched.
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
- Second lease approved by Chief after the first fix: read-only
  `app/src/main/java/at/heinz/deviceai/learning/ContinuumTemporalMemory.kt` and
  `app/src/test/java/at/heinz/deviceai/learning/ContinuumTemporalMemoryTest.kt`.
- This handoff and `docs/evidence/omega-intelligence-20261103/`.
- Trace actual context/authority flow, freeze a meaningful negative regression,
  observe RED, minimally repair the same projection, run focused and available
  integration tests, record exact source and logs, push each meaningful checkpoint.
- Confirmed root cause: entity segments are clipped to 80 characters and only
  pre-number negation/hedging was checked. Denial before the entity or after the
  selected number became an affirmative current count. Existing qualifiers now
  inspect the complete quantity sentence before a value is admitted. Parsing
  limits remain unchanged; a separate no-save instruction does not assert facts.

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
- RED: 12 actual JUnit tests, 4 assertion failures against baseline production;
  suffix denial, late denial beyond 80 characters, suffix uncertainty, prefix
  denial each wrongly produced original=4/current=9. Eight controls passed.
- GREEN: same 12 tests passed after the minimal fix; broader pure context subset
  22/22 passed. Compiler 2.4.0, JUnit 4.13.2, actual OpenJDK 21.0.12.1, JVM target
  17, no stubs or mock Kotlin classes. Commands:
  `bash docs/evidence/omega-intelligence-20261103/run-timeline-jvm.sh`
  and the same command with `--context`.
- Raw logs: `red-jvm.txt`, `green-jvm.txt`, `context-jvm.txt` in that evidence
  directory. Logs bind base SHA plus exact source/test SHA-256 where the tree was
  dirty. Production GREEN SHA-256:
  `cacf4f226e9b13555a7e49bf9a6fc36b41f56a58a8a20608111de9dda3b884d1`;
  test SHA-256 `8491828e0068bc9354559df8ffffabc8b86fd1900aa5e3506c74be9a5ab2adfc`.
- One wider isolated compilation attempt was UNAVAILABLE because its manually
  selected source set omitted ContinuumTaskClassifier/ChatReasoningRouter. Raw
  failure retained in `context-incomplete-source-set.txt`; no test ran in that
  attempt. The passing 22-test subset does not include the dependent frame/budget
  classes. Full graph verification belongs to the Gradle gate below.
- Android/Gradle/full suite: NOT_RUN; SDK readiness owned by Quality lane.
- Limits: conservative whole-sentence abstention can reject a true number with
  an unrelated negated clause. General pronoun corrections, number-free
  retractions, multi-number ambiguity and model response quality remain open.
- Next: pin verification to the committed fix, get Quality full-graph results,
  inspect the already-consumed A8 employer-retraction miss as a separate possible
  regression (never report it as new blind evaluation gain).
- Rollback: revert only this lane's source/test commit; no migration or data
  rollback required. Preserve all logs and negative results in this branch.

## Second regression — known employer retraction miss

The already-consumed frozen German LME A8 case reports stale employment after
`Ich habe gekündigt.` (historical aggregate 34 correct / 5 abstained / 1 wrong).
This is a known regression target, not fresh blind evaluation. The frozen corpus
is unchanged. Root cause: `ContinuumTemporalMemory.mentions` recognises WORK only
through `arbeit`, so the existing unparsed-statement uncertainty barrier never
applies to the bare resignation statement.

Six separate regression/control methods cover current clarification, preservation
of past/as-known answers, working-session isolation, unrelated contract/insurance
cancellation and denial/other-person text, hypothetical/uncertain/question/planned
text, and revoked resignation. Actual baseline RED: 19 JUnit tests / 2 failures
(ledger and working-memory resignation both incorrectly ANSWER); 17 pass. Log
`temporal-red-jvm.txt`, command `bash docs/evidence/omega-intelligence-20261103/run-temporal-jvm.sh`.
The compile reports four pre-existing unnecessary-`!!` warnings; no Android/stubs.
Next: exact bare owner resignation adds only an UNCLEAR barrier, never a guessed
employer or unemployment fact. Source remains untouched at this RED checkpoint.

First fix exact replay: `5346e76910bb19e2da859556852724c2c2fe3c59`, 22/22 PASS,
`verified-5346e769.txt` (source/test files clean; only the output log untracked).
