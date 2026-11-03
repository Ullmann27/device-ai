# Continuum Ω cognition breakthrough lane — 2026-11-03

Status: SOURCE_INTEGRATED / focused HOST_VERIFIED. Combined Gradle gate pending.
Draft PR: https://github.com/Ullmann27/device-ai/pull/398.

Base: Chief PR #392, `64ec06a73f50cae9ee49035c840710d87777d50f`, verified via
GitHub PR metadata. Branch: `codex/breakthrough-cognition-20261103`.
The current explicit owner instruction authorizes isolated work, tests, push and
Draft PR. The historical v31 freeze is superseded as documented in
`docs/release/CONTINUUM-RELEASE-STOP-RECONCILIATION-20261103.md`.
AGENTS, experiment contract/register, live Issue #41, current-state/frontier
entrypoints, brain inventory and evaluation-v1 were read. EXP-05/06/07/11/12
interfaces remain unchanged. No main merge, release, signing, installation,
actual owner data, persistence, model weights or authority changes.

## Owned paths

- `app/src/main/java/at/heinz/deviceai/cognitive/OwnerStatementTimeline.kt`
- `app/src/test/java/at/heinz/deviceai/cognitive/OwnerStatementTimelineTest.kt`
- A focused same-package context integration test only if needed.
- This handoff and `docs/evidence/breakthrough-cognition-20261103/`.

## Pre-registered hypothesis and decision rules

The existing timeline skips a sentence before qualifier checks when it contains
no extracted number. A later owner's number-free denial or uncertainty naming an
already known entity can therefore leave a stale affirmative current quantity in
the required model prompt. This is a deterministic context bug, not a claim about
general reasoning or Gemma quality.

Baseline: unmodified Chief production source above plus new development tests.
Record actual failing assertions and preserve complete RED logs. The exact
baseline is otherwise already reported by Chief as 2101 total, 2089 pass and 12
ignored, with no failures; that historical receipt is not this lane's execution.

Success: number-free denial/uncertainty of the matching entity invalidates the
derived projection; original owner messages remain available to the existing
model context; plain numerical updates and existing original/current/future
controls remain green. No zero/replacement quantity is guessed. Use the existing
whole-projection null fallback, not a second state or reasoning engine.

Negative/stop criteria: any known correct test regresses; unrelated entity text,
number-free questions or separate no-save instructions wrongly invalidate the
projection; authority/persistence/model changes are required; new blind failures
are used to tune the submitted candidate. Freeze a committed candidate before
requesting independently authored QA results. M3 remains a consumed development
set and its routing expectations must not cause memory-consent changes.

## Actual path and limits

`AgentActivity.runLocal` builds `LocalFollowUpTaskFrame` from
`LocalConversationContext.sessionEvidence`. Its `BoundedAgentOrchestrator` model
callback invokes `AiContextCompiler`, requires full owner history for recognized
follow-ups, and appends `OwnerStatementTimeline.project(...).render()` to
required prompt body. `LocalPromptFrame` and `LocalContextIntegrityProbe` then
enforce complete history. Tool/write authorization retains untouched owner text.

`facts()` currently runs `if (sentenceFacts.isEmpty()) continue` before applying
negation/hedge/conditional/past qualifiers. The candidate will address this same
projection boundary. Exact recognized entities and existing bounds remain;
pronoun-only retractions and broader natural language remain outside the claim.

## Verification and continuation

Focused real Kotlin/JUnit first, shared toolchain under
`/workspace/continuum-build-env`; coordinate Gradle with Chief/QA. Commands and
raw logs will be preserved in this lane's evidence directory. Native local
Gemma/Fold7 are unavailable in this environment; no inference/device result.

Rollback: revert only this lane's scoped production/test commit. No data migration
or owner-memory rollback. Next: add focused regression/control tests and record
RED, then minimally fix and re-run unchanged tests before freezing for QA.

## Development result and candidate freeze

Root cause confirmed: the unmodified timeline returned stale affirmative 4/6
after four independently asserted number-free owner denial/uncertainty cases.
The candidate passes known entity units into the existing bounded parser and
checks number-free clauses before skipping them. A present quantity predicate,
the recognized same entity and counted unit, a denial/hedge, and only permitted
modifiers must all match. It returns the existing whole-projection null fallback.
No zero, new fact, persistence, consent or model authority is inferred.

New context integration test:
`app/src/test/java/at/heinz/deviceai/cognitive/OwnerStatementTimelineContextIntegrationTest.kt`.
It exercises actual `recordUnanswered` → `sessionEvidence` → follow-up task frame
→ timeline → required prompt frame and integrity probe. Original owner text
remains in the model prompt while the stale derived summary disappears. Model
answers and other-session denials cannot invalidate owner quantities.

Actual real Kotlin/JUnit runs (JUnit 4.13.2, Kotlin compiler 2.4.0, OpenJDK 17.0.20.1):

- `red-jvm.txt`: 28 tests, 4 assertion failures, 24 passed. Production still
  byte-identical to Chief baseline; tests and sources hash-bound in the log.
- `green-jvm.txt`: same 28 tests passed after the source change.
- `context-red-jvm.txt`: baseline replay with unchanged expanded tests,
  57 tests / 5 failures / 52 passed. Fifth failure is the stale derived quantity
  present in the actual context/prompt component composition.
- `context-green-jvm.txt`: identical 57 tests passed against candidate source.
  Runner `bash docs/evidence/breakthrough-cognition-20261103/run-context-jvm.sh`
  supports `--baseline` for the exact 64ec06a7 source replay.
- Integration runner compiles exact changed source/tests against a copied
  snapshot of actual Gradle-produced baseline dependency classes, with no stubs.
  QA confirmed those dependencies match Chief 64ec06a7; `git diff 64ec06a7
  e934ba31 -- app/src/main` is empty. Snapshot aggregate hash is
  `9f1f7524d8490f7c5d021b87de0107fc0c31334181362ab0e78392bf9320b3ba`.
  This focused host composition is not an Android Activity/inference run.

Negative controls retain quantities for different entities/units/properties,
no-save instructions, questions, hypotheticals, quotes/reported speech, nested
negation, and past/future number-free clauses. The original 12 timeline methods
and surrounding existing context/retention/prompt methods remain unchanged.

Limits: the bounded present-clause grammar is deliberately narrow. Pronouns,
missing counted units, plural/hard paraphrases, nested or ambiguous prose and
unsupported retractions are not a general semantic solution. Whole-projection
abstention also removes unaffected derived entries while preserving raw owner
history. A later positive restatement after a retraction still conservatively
abstains within that retained history. Full project and independent frozen tests
remain pending. Native Gemma/Fold7 are unavailable. No broad intelligence gain.

Candidate source is frozen in the commit containing this section, before any
independent unseen test result is requested. Future unseen failures will be
reported without tuning this frozen candidate.
