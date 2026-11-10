# Cognitive budget experiment — first checkpoint

Status: WIP / UNVERIFIED. Owner-authorized role A, integration owner: Cinema root integrator.
Canonical target: `cinema/fluid-light-stage-20261110` / PR715.
Assigned source: `4ac9c9b94b9fb5e391646e2e99e986db32ec7997`.
Research branch: `research/continuum-cognitive-budget-20261110`.
Experiments: EXP-05 / EXP-11. No production promotion, main merge, signing or device claim.

## Existing source audit

- `cognitive/Deliberation.kt`: strategy selector, bounded budgets, cancellation,
  candidate/critic/verifier/judge orchestration already exist.
- `cognitive/ContinuumIntelligence.kt`: task classifier and reasoning pass DTOs.
- `cognitive/LocalModelReasoningAdapter.kt`: local `streamThinking`, model pinning,
  deadline polling and rejection of tool proposals; reuse this adapter.
- `cognitive/ChatReasoningRouter.kt`: existing optional hard-task advisory path.
- `localmodel/LocalModelComputeBudget.kt`: load-time thermal CPU thread budget;
  no live engine reconfiguration will be claimed.
- `chief/ChiefBotTeam.kt`: existing ANALYZE/REVIEW specialists and owner approval;
  no additional worker pool, event bus, tool authority or memory database.
- `cognitive/ChatSpecialistChain.kt`: existing owner-switched memory specialist chain.
- `cognitive/ComputeFabric.kt`: signed worker capabilities/receipts; this experiment
  does not enroll compute nodes or replace their verifier.
- Live PR716 `00cf15d9`: CTX3 negation shadow fix remains separate, unpromoted.
- Live PR715 initially reported `11f756e6`; root confirmed assigned local `4ac9c9b9`
  is intentional and awaits its publishing checkpoint.
- Existing PR287 and Issue41 contracts read; historical PR/branch catalog scoped
  to cognition/budget/research candidates. This is not an exhaustive history audit.

## Bounded first milestone

Reuse existing strategy/budget/cancellation/model-call contracts. Add a default-off
research policy for FAST/NORMAL/DEEP/TOOL/MEMORY/RESEARCH. Only first three execute
local repeated inference. Other modes require their existing authorized caller.
Maximum four sequential calls; adaptive stop only for identical/repeated output,
never a correctness score. App-level repeated inference is **not recurrent
transformer depth**, weight adaptation, self-learning or independent verification.

Integration decision: retain only as an explicit research callable if default-off,
cancellation, changing model, tool proposal, monotonic deadline, UTF-8/call budgets
and deterministic fixed-vs-adaptive controls pass. Root owns optional UI wiring.
Reject production promotion absent actual model/device quality and thermal trials.
Rollback: remove the new research entrypoints; existing chat remains unchanged.

Tests: NOT_RUN. Next: implement policy and bounded adapter runner, scoped host/JVM
regressions and source-hashed controlled A/B. No native inference/cloud bill.

## Verified milestone — source 2824c3718e560ce3f369eb20666b434b99db1c39

The WIP section above records the initial checkpoint. Current executable state:

| Gate | Result |
|---|---|
| PORTIERT | Explicit research entrypoint in own branch; canonical Cinema integration pending |
| VERIFIZIERT | 47 scoped JVM tests PASS; 36 new + 11 existing; no skipped tests |
| GERÄTEBESTÄTIGT | NOT_RUN |
| Native/local model inference, Android build | NOT_RUN |
| Local factory compile | Pending root's isolated main-source compile |

Files: `cognitive/CognitiveBudgetController.kt`, `CognitiveBudgetResearchRunner.kt`,
`CognitiveBudgetLocalResearch.kt`. Existing files/engines/permissions are unchanged.

- Controller consumes existing `ReasoningTaskProfile` and reuses the existing
  selector, `DeliberationBudget` and load-time `LocalModelComputeBudget`.
- FAST/NORMAL/DEEP allow at most 1/2/4 calls. Unknown or moderate thermal status
  limits this experiment to one call; severe or higher blocks it.
- TOOL/MEMORY/RESEARCH admit zero model calls and return `EXISTING_PATH`.
  A mode is not a tool approval, memory entitlement or network permission.
- `CognitiveBudgetLocalResearch.create(existingGuardedRuntime, sourceRevision,
  config)` provides an explicit local factory through `LocalModelReasoningAdapter`.
  Default config is disabled; construction and disabled run issue no inference.
- Runtime caller must already own the existing inference lane. The factory adds
  no worker pool or scheduler. Its private epoch clock advances monotonically,
  so wall-clock corrections do not enlarge native pass deadlines.
- `runner.cancel()` forwards to the existing adapter while a call is active.
  Runner instances are single-use. Synchronous third-party test adapters must
  cooperate; the runner cannot forcibly terminate arbitrary blocking code.
- Whole task/context survive each pass. Excess UTF-8 input/output is rejected,
  never silently clipped. Maximum cumulative output is 8,192 bytes by default.
- Identical output or a repeated candidate hash stops further inference. These
  are cost heuristics, not factual validation. Candidates remain unverified.
- Receipts contain source revision, task/context/prompt/output hashes, attempted
  versus completed calls, observed bytes and elapsed time. No tokens/joules or
  fictitious money estimates. Hashes are linkable, not anonymization. Supplied
  source revision is provenance metadata, not signed execution attestation.

### Actual controlled A/B

64 scripted fixtures, alternating arm order, same maximum of four calls:
fixed256 / adaptive192. Candidate equality48/64. Non-cyclic cases48/48 identical;
all16 oscillating cases differ because adaptive stopping ends at the first repeat.
This negative control means the experiment does **not** establish answer quality
or a safe automatic live-dialogue promotion. Time measurements in TSV include JVM,
controller and receipt overhead; they are not local-model latency measurements.

`mode-receipts.tsv` uses an explicit synthetic NEW_TASK label, not the event bus:
FAST1 call; NORMAL2 and DEEP2 identical-output stop; TOOL/MEMORY/RESEARCH0 calls.
The lab can reference these receipts but cannot turn event salience into authority.

Evidence: `docs/evidence/cognitive-budget-20261110/` includes exact input hashes,
full fixture rows, JUnit output, compiler/JDK provenance and acceptance limits.
The host script extracts the **unchanged actual reasoning DTO declarations** from
`ContinuumIntelligence.kt`; it does not compile the whole app or substitute a model.

### Reproduce

Set KOTLINC, JAVA and CONTINUUM_JUNIT_JARS to installed Kotlin/JDK/JUnit4/Hamcrest,
then run `bash scripts/continuum-cognitive-budget-check.sh /tmp/cognitive-budget-check`.
Used Kotlin2.4.0 / JDK21; no download, added dependency, Gradle or real inference.
`git diff --check` passes. New files only; source hashes verify the bound inputs.

### Integration decision and next gate

Root owns integration into PR715. Accept explicit experimental research API after
its complete main-source compile and review; keep it disconnected from ordinary
chat, autonomous actions and learning. Do not interpret this API as recurrent
transformer layers, increased physical compute, trained weights or independent
verification. Live promotion requires separately authorized model/device tests,
quality holdout, cancellation/thermal measurements and existing lane ownership.
Rollback is removal of the three new research source files and optional caller.
No existing production path needs migration.

Public research publishing was temporarily paused after root reported an automatic
approval-review rejection; the owner subsequently explicitly authorized immediate
pushes on assigned research branches. The final checkpoint uses that authorization.
No main merge, protected release edit, signing or APK operation is included.

## Android main compilation follow-up

`ANDROID_MAIN_KOTLIN_COMPILE_PASS`: complete Android `:app:compileDebugKotlin`
succeeded with17 tasks in1m21s, using existing SDK36/AGP8.10.0/Kotlin2.4.0/JDK21.
This closes the earlier local-factory **compilation** gap, not native inference.

Actual QA commit `25766f72cd511f247950c7d558fe323be6be2d4c`, complete Git tree
`c15f1ab11e0777fac53f0d6a6b77b282bebe9016`, app main-source tree
`db972eea5028e650361907777c863510a1f14c60`. It combines local root4209453 with
the three budget sources and independently reviewed `ContinuumMemorySkillResearch`.
All four input SHA256s and emitted class hashes are in
`docs/evidence/cognitive-budget-20261110/android-main-compile/receipt.json`.
The raw successful log and command are adjacent. No production caller was added.

Root4209453 was not individually published. Remote root
`2eca96cf50df2a4f4538f61cac911aa90563a93d` is main-source identical; its later local
9fd5d14 difference is test setup only. This is **not** full-tree equivalence.
Reconstruct this main-source tree from that published root and the four pinned
research files. Main-source compile does not run the unit tests or establish APK,
model, learning, Fold7, thermal or later Cinema revision acceptance.

The QA-only worktree and branch remain separate. This receipt is published only
on this cognitive research branch; the root graphics branch stays untouched.
