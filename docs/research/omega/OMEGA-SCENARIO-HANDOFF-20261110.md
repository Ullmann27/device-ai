# OMEGA downstream scenario and dispatch-state correction

Owner: OMEGA root integrator. Target: research/omega-work-contract-20261110,
then Cinema #715 only after its integrator reviews the exact combined source.
Base local515afc28 / remote28bee009 share tree735a663d522e16d56e979c78a908e9f3baa2f031.

## Observed defect and minimal change

The existing BoundedAgent callback emitted EXECUTING while ToolGate's durable
receipt was still START_PENDING. Cancelling from that event produced zero
backend calls, and failing durable dispatch persistence also still emitted it.
The cockpit's dedicated onToolStarted callback already used the later boundary.

Move only the existing EXECUTING emission into the existing onDispatchStarted
callback. This aligns the two signals after DISPATCH_COMMITTED. No additional
bus, state machine, store, permission or retry loop. A cancellation after this
boundary remains outcome-uncertain; it must not be advertised as definitely unrun.
DISPATCH_COMMITTED means the backend dispatch was committed, not that the tool
succeeded or achieved the user's goal.

## Scope of the host scenario

The exact animation utterance is passed unchanged as the orchestrator request.
A deterministic test adapter supplies a read-only find_files plan against one
synthetic animation filename. The backend is an explicitly labelled HOST_FIXTURE.
This tests downstream policy/evidence/presentation continuity; it does not parse
the request, inspect real animation pixels or improve animation source.

| Stage | What is actually exercised | Boundary |
|---|---|---|
| Context/reference | Separate OMEGA working-context prototype | Not supplied by this suite |
| Plan | Existing ReadOnlyToolPlan with explicitly supplied synthetic target | No model planner |
| Permission | Actual ToolGate defaults; unchanged approval provider | Generic assent cannot set approval |
| Web consent | Actual ApprovedWebToolGate exact replacement ticket, one-shot/revocation checks | No network request |
| Execution | Actual ToolGate thread and onDispatchStarted | Fixed injected host backend |
| Verification | Actual plan evaluator and fresh output receipt | Verifies listing only, not improved animation |
| Memory | Actual InMemoryExperienceStore and candidate compiler boundary | Not owner facts, not durable skill promotion |
| Visual work state | Actual CockpitAnimationTriggers and bus | No renderer, GPU or displayed frame |
| Response | Existing deterministic plan formatter | No Gemma quality or speech test |
| Late/stale result | Closed cockpit turn suppresses late starts/drafts | No device lifecycle measurement |

The positive control permits a valid exact web ticket and a valid read-only
listing. Denial alone is not counted as full success. Failure, replay, zero
budget and form-only evaluation cannot establish a successful work outcome.

## Measurements and source binding

Baseline: 10 scoped tests, 3 failures at the dispatch-state invariant.
Candidate: 10 scoped tests, 0 failures. Exact source hashes and logs are under
docs/evidence/omega-work-contract-20261110/scenario/{red,green}/.
The four unchanged regression classes add 21 passing methods (31 distinct
methods including the scenario suite). Reproduce together by setting
CONTINUUM_OMEGA_REGRESSIONS=1 in the command below.
All ten critical production sources are recompiled directly; transitive app
dependencies come from the root's frozen full Android compilation, with a
recorded whole-class-manifest digest. No compile stubs are used.

The green source-commit.txt identifies its parent checkpoint; the three-line
callback patch was present in that run and is bound by source-sha256.txt. Parent
integrator must run the same check after integration for final commit attribution.
The supplementary red cancellation receipt was captured in the initial
development harness on identical baseline production bytes; the final red
result.txt is the definitive same-test before/after comparison.

```bash
KOTLINC=/path/to/kotlinc \
CONTINUUM_APP_CLASSES=/path/to/app/build/tmp/kotlin-classes/debug \
CONTINUUM_JUNIT_JARS=/path/to/junit.jar:/path/to/hamcrest.jar \
bash scripts/continuum-omega-scenario-check.sh /tmp/omega-scenario
```

## Remaining limits

AgentActivity's permission-derived RESEARCH work-scene selection and its
turnEnded delivery flag were not changed. They require a separate caller-level
review before claiming every rendered scene reflects a real operation.
General semantic planning, real animation changes, model inference, Fold7,
audio/lip/eye/head sync, CPU/thermal and device memory are NOT_RUN here.
This suite is development verification, not the independent unseen holdout.

Rollback: revert the BoundedAgent callback relocation; remove no existing bus,
receipts or gates. Keep the negative source-bound evidence.
