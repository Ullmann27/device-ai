# Three tuning bots — round 2, 2026-11-04

## Owner continuation and frozen baseline

Heinz requested “Weiter” after the completed first tuning round. Exactly the same three helpers continue only existing-component tuning. This is a bounded second round, not a new feature, repair, integration, training or release assignment.

- Review: [PR #511](https://github.com/Ullmann27/device-ai/pull/511), branch `tuning/continuum-three-bots-20261104`.
- Freshly read remote baseline: `04b46f125d1086e6ea540d3c68700407f340f607`.
- Equivalent local baseline used by the helpers: `db3295d51c769afdaf809ac1dfae2ada84a3274f`.
- Complete shared tree: `d753188fe0e67f6c6f098db6b7073ea199e05530`.
- The original pre-tuning source `e101e393dd6c80803ac9d9ac91fccd9812c4a18d` is historical. Incremental round-2 measurements must compare with the round-1 baseline above.
- Round 1 remains documented in [its completed handoff](THREE-TUNING-BOTS-HANDOFF-20261104.md), with all unfavorable controls retained.

## Assigned narrow candidates

| Helper | Existing function | Candidate and key constraint |
| --- | --- | --- |
| `tuning_model_inference` | `LmEval.sample` | Replace boxed sampling weights with a primitive array; preserve calculation order, Float/Double behavior, seeded output, RNG calls, empty-input exceptions and greedy behavior. Address the known positive-temperature allocation cost. |
| `tuning_memory_context` | `AiContextCompiler.redactSecrets` | Reuse exactly the existing two compiled regular expressions. Keep replacement rules/order intact, create per-call matchers, retain no input text, verify concurrent calls and first-use cost. |
| `tuning_runtime_workers` | `depsDone` in the canonical backlog | Replace repeated linear ID scans with binary lookup only after confirming sorted-ID invariants through every caller, batch and migration. No cache, new schema or extra work for empty dependency lists. |

These are candidates, not accepted performance results. Each helper uses an isolated branch/worktree. Existing source interfaces and EXP-01/05/06/08/11/12/14 boundaries remain intact. No new agent framework or external AI dependency is introduced.

## Acceptance and measurement protocol

1. Bind the exact original and candidate source hashes. Reuse the existing real source graph and relevant regression suites.
2. Compare complete outputs, expected exceptions, mutation/state effects and RNG where applicable. Preserve safety/privacy semantics and report any counterexample.
3. Predeclare bounded workloads, warmup and alternating A/B rounds. Keep controls with no expected gain, all raw samples and failed/rejected candidates.
4. Serialize this team's timing windows; other chats' host load remains uncontrolled. Functional checks may run in parallel before timing.
5. LM: existing greedy controls plus positive/NaN-temperature cases. Context: reused and fresh compiler instances, concurrent correctness checks, and at most three exploratory fresh-JVM first-use pairs. Worker: 0/1/20/100/500 tasks, dependency-free controls, early/late/16-dependency cases and state changes between calls.
6. Accept only measured reductions without unexplained behavior changes; an unsuccessful optimization is recorded as rejected. A host microbenchmark does not establish Android/Fold7, whole-model, battery or frontier-model performance.

## Fresh coordination and workspace state

At round start, PR #511 still points to the completed round-1 source and remains a draft. The current changed-file lists of #505, #512, #513, #515, #516, #492 and #514 were checked. Their current production deltas concern separate repair, avatar/integration or experimental training scopes; none overlaps `LmModel.kt` or the Node backlog module. #506 contains a separate context integration; our work stays inside the existing private redaction helper. #494's retrieval optimization is not repeated.

Issue #41, PR #287, repository instructions and the relevant experiment entries were rechecked. No other branch is modified. The shared filesystem initially lacked room for another full checkout; the existing three clean helper checkouts were reused on new round-2 branches with a reversible sparse source selection. Source commits and user data were retained. No toolchain download or large build was started.

## Checkpoint status

`WIP / CANDIDATES_NOT_YET_ACCEPTED`. Three helpers are preparing the small patches and source-bound functional checks. No round-2 timing result is claimed at this checkpoint. New evidence belongs under `docs/evidence/three-tuning-bots-r2-20261104/`; round-1 observations must not be overwritten.

Root will independently review/test the combined accepted source and publish it with exact Git-tree equality. Production/main merge, APK/installation, model promotion, permanent background operation and frontier superiority remain outside this task's verified outcomes.
