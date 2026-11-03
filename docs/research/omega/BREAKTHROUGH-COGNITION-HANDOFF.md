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

Frozen candidate: `107a7bb0ce680ad0c362b4226308942aa0dfaaec`, pushed to PR #398.
Exact committed replay `frozen-107a7bb0-jvm.txt`: **57/57 passed**, no warnings;
production SHA-256 `375173118c83a9568610c3ad03742109317dadcdba238461f5a608ff6000dd03`.
The source hash differs from the earlier green log only because the class comment
was updated before freezing. QA received the frozen SHA before independent results.

## Unchanged consumed M3 comparison

Runner modes `--m3-baseline` and `--m3-candidate` compile the unchanged actual M3
scorer against the respective timeline and the same real baseline dependencies.
Both score **77/81, 0 harmful**. All 81 per-case outputs are byte-identical;
8/8 MULTI cases remain correct. Baseline and candidate report SHA-256 are both
`7db43dd5fd837aef24cc22cc233cd2378f6a9895c25004072be5418576b7eef1`.
`m3-comparison.json` records **0 improvements / 0 regressions**, using the unchanged
existing comparator. The corpus hash still matches evaluation-v1. This is known
development-set regression evidence, not an independent or model-quality gain.

All four existing misses remain R06/R07/R10/R17 route NONE. Caller inspection
confirms this scorer checks `OwnerChatIntentRouter` and `BootstrapBrain` only;
the actual Activity also has the existing local-model/orchestrator paths.
`BootstrapBrain` deliberately recognizes colon-form memory commands and exact
forget-all phrases; Activity feeds REMEMBER into `rememberOwnerFact` and FORGET
into owner confirmation. Changing those routes merely to match this proxy would
change authority semantics and is outside this fix. No scorer, corpus, route,
memory admission, consent or Activity code changed.

## Authorized bounded follow-up — pre-registration, 2026-11-03 18:46 UTC

The initial independent 24-case QA result is **8/24 baseline, 8/24 candidate**,
not a broad gain. Its 16 withdrawal/uncertainty cases failed both implementations;
all 8 unrelated retention controls passed. The exact published fixture and QA
harness were rerun without modification against Chief64ec and frozen107a,
reproducing that result. Logs are under `followup-development24-red/`.
These 24 cases are now consumed development material, never fresh evidence again.

Chief explicitly authorized one follow-up on the same paths. QA froze a separate
20-case reserve before this work; its hash is
`9c94f5ee88c4bcb42a60efa1e5eab6d2afb8d54129ac971700eba27364d77947`.
The reserve contents have not been inspected. Only the published 24-case fixture,
runner and results were read. Earlier candidate107a remains preserved unchanged.

Single hypothesis: the missing layer is a bounded *quantity-assertion reference*.
Owners withdraw the count's claim (Angabe/Anzahl/Zahl/Menge), rather than repeat
a possession predicate and counted unit. Add recognition for complete owner
withdrawal/uncertainty clauses that resolve their quantity reference to a known
entity or counted unit. Keep the same null-projection fallback, no inferred zero,
new fact or competing reasoner. Support explicit old-count mentions as uncertain
evidence; never promote the denied count into a current assertion.

Plan before source changes: preserve a new authored RED for entity/unit-referenced
withdrawal, uncertainty and old-count context; then add one bounded parser change.
Reject quoted/reported/hypothetical clauses, nested negation, no-save instructions,
unrelated properties/units/entities. The complete sentence must match the grammar;
a keyword appearing elsewhere is insufficient. Original owner history stays intact.

Success/stop rules: original57 and unchanged M3 must not regress; all observed
development failures/limitations remain visible. Freeze the new production/tests
before the private20 result is requested. Fresh evaluation must improve over its
same-source baseline without any previously passing case regressing; otherwise
report no supported unseen benefit. No tuning after the reserve result in this
bounded follow-up. Combined Gradle remains Chief/QA-owned; no model/device claim.

Follow-up development checkpoint: six authored methods added without changing
the original57 methods. Real RED: **63 tests / 4 failures / 59 pass** against the
unchanged107a source, preserved in `followup-authored-red.txt`. The single grammar
change then passes **63/63**; initial compile warnings are retained in
`followup-authored-green.txt`, and `followup-final-green.txt` records the clean
rerun after removing unnecessary non-null assertions and requiring a concrete
entity/unit reference. No private-reserve result has been requested yet.

The grammar recognizes a complete quantity-claim withdrawal, unreliable status
or first-person uncertainty, then resolves its reference through existing entity
keys/adjective stems and counted units. A mentioned old count is reference data,
never a newly asserted value. Unknown entities/units, no reference, quoted or
conditional text and unrelated properties do not match. The same all-or-null
fallback preserves raw chronological owner text. Unrecognized natural-language
forms remain possible; no general semantic completeness claim is made.
