# Independent recall ordering evaluation — result

Decision: **REJECT_PROMOTION for candidate ad995a37463b110760e880a74db7cf1d806dd492**.
The preregistered no-regression condition failed. The candidate improves three
controls but regresses one control which passed on the unchanged baseline.
No implementation change or further tuning was made by this evaluator.

Baseline: `515afc28af11b9befeb5a61f9c44411d423635a9`.
Candidate: `ad995a37463b110760e880a74db7cf1d806dd492`, tree
`cf9855670dee83036c7e55f27cae2b3e927700cf`.
Specification frozen at `fe5d97dfc8d9705d7b7ac154c8a4df5055ef641d` before candidate
inspection. Executable tests initially frozen at `04997670bbb583720170a31c94f477b92fdf9291`.
The exact tested fixture is at `f6e869e039fd866b9f8444ecd890ed951af93ab0`; runner at
`d05350c`. All fixture corrections preceded candidate inspection.

| Control | Baseline | Candidate |
|---|---|---|
| R1 newest equal-relevance success hints | FAIL | PASS |
| R2 latest failure warning beyond old 64-record window | FAIL | PASS |
| R3 lexical relevance before recency | PASS | PASS |
| R4 deterministic ID ordering on equal relevance and time | PASS | PASS |
| R5 age boundary, present and future rejection | PASS | PASS |
| R6 future records cannot evict valid evidence | PASS | **FAIL** |
| R7 stale records cannot evict latest warning | FAIL | PASS |
| R8 unchanged unauthorized-tool behavior | PASS | PASS |
| Total | **5/8** | **7/8** |

R6 used one valid success, ID `a-valid`, completed at `now−1`, plus 64 otherwise
matching records `z-future-00` through `z-future-63`, completed at `now+1`. All
requests have identical relevance. The baseline admits the valid record to the
64-record window and returns its unverified hint. The candidate ranks all future
records first, limits to 64, and then discards them, returning no hint.

Cause: `MemoryEcology.kt:235` newly orders equal-relevance records by descending
`completedAt`, while `:237` applies `take(limit)`. `AgentExperienceRecall.kt:25–33`
performs the current-time eligibility check only after the store returns that
bounded window. This is an observed equal-relevance regression, distinct from the
already known case of higher-relevance ineligible records consuming the window.
The test does not prove a frequency or incidence rate in owner data. It proves
that the narrow ordering change fails the declared correctness contract.

R8 preserved zero tool-runner executions with recall both disabled and enabled.
The external-content guard rejected the synthetic shell proposal before dispatch;
there was no approval grant. Historical data remained UNVERIFIED,
MODEL_GENERATED, DEVICE_LOCAL_ONLY with zero confidence, and were absent from
stored owner-memory references. This control used a deterministic AgentModel;
it did not execute an actual LLM or Android tool.

## Reproduction and evidence

Actual `MemoryEcology.kt`, `AgentExperienceRecall.kt`, and `BoundedAgent.kt` from
each exact source were compiled in front of the same immutable application-class
dependency snapshot. No substitute ToolGate or ExperienceStore implementation was
used. Dependency manifest, exact source hashes, compiler details and unedited
JUnit output are in
`docs/evidence/continuum-learning-recall-eval-20261110/`.

Set the explicit `RECALL_*` dependency/compiler paths required by `run.sh`, then:

```sh
docs/evidence/continuum-learning-recall-eval-20261110/run.sh BASELINE_CHECKOUT EXTERNAL_BASELINE_OUTPUT
docs/evidence/continuum-learning-recall-eval-20261110/run.sh CANDIDATE_CHECKOUT EXTERNAL_CANDIDATE_OUTPUT
```

The first baseline execution exposed a fixture mistake: the test assumed every
refusal produces a ToolGate receipt, but the existing earlier external-content
guard correctly refuses before a receipt exists. The original failed output is
retained in `baseline-initial-fixture-error.txt`. The assertion was corrected to
check absence of authorized/successful calls, a non-null identical blocker, and
identical call traces. An earlier `finalState` property typo was corrected to the
actual `state` before compilation. Neither changed the preregistered safety
requirement. One corrected baseline run and one candidate run were then performed;
there was no candidate-driven fixture modification.

JUnit elapsed times were 0.168 s baseline and 0.189 s candidate for the small
control suite; they are not response-latency, CPU, memory, or model-performance
benchmarks. The implementer's separate 28 tests are not included in these eight
independent controls. Android/Fold7, real model inference and device resource
measurements: **NOT_RUN**.

## Handoff and boundaries

Integrator `/root` owns `research/continuum-learning-recall-20261110` and remote
publication. This evaluator branch changes only tests and evidence. Source remains
at the frozen baseline; protected Build 52, main, releases and signing unchanged.
Rollback/promotion decision: retain baseline behavior pending a separately reviewed
correction; this report does not authorize another experiment loop.

The earlier identified sequence-loss limitation also remains open:
`BoundedAgent.kt:537` stores `usedTools.distinct()`, whereas ExperienceCompiler and
PR #719 group ordered tool lists. Repeated steps cannot be recovered from existing
lossy episodes. No sequence patch or historical provenance reinterpretation was
made. This limitation is not part of the current ordering change or its eight
controls, and is not a claim that procedural skills are autonomously executable.
