# Independent recall ordering evaluation — preregistration

Status: SPECIFICATION_FROZEN, executable controls NOT_RUN.
Baseline: `515afc28af11b9befeb5a61f9c44411d423635a9`.
Evaluator branch: `research/continuum-learning-recall-eval-20261110`.
Integrator: `/root`; candidate branch `research/continuum-learning-recall-20261110`.
The evaluator inspected baseline source and existing recall tests only. No candidate
source, diff, development tests, or outputs were inspected before this commit.

Scope: one bounded independent JVM comparison of the existing ExperienceStore →
AgentExperienceRecall → BoundedAgentOrchestrator path. No model weights, new stores,
buses, tool permissions, or production activation. The earlier OMEGA architecture
experiment remains REJECT_PROMOTION. This is a separate correctness repair.

All records are synthetic. Fixed time is 5,000,000,000 milliseconds. An eligible
success has SUCCESS, evaluator success/0.95, a registered tool, and an existing
historical-output digest. Hints remain UNVERIFIED; those labels and digests do not
independently prove repair success. Failed records carry evaluator failure and a
synthetic failure note. Request is `inspect cooling sensor` unless specified.

| ID | Independent control | Fixed expectation |
|---|---|---|
| R1 | Store 65 equal-relevance successes at increasing completedAt with IDs in the opposite age order. | Newest two are the two success hints. This is the intended failing baseline / passing candidate test. |
| R2 | Store 64 equal-relevance older successes, followed by a latest failure with a lexically last ID. | Latest failure warning survives the existing candidate cap; success hints remain at most two. |
| R3 | One older exact-request record and one newer `inspect cooling unrelated` record. | Exact-request record ranks first; recency never overrides better lexical relevance. |
| R4 | Four records with equal request and completedAt, inserted in ascending, descending, and fixed shuffled ID order. | Store result is ascending ID and identical for all insertion orders; positive hints reflect that same deterministic first two. |
| R5 | Separate stores with age exactly 30 days, 30 days plus one millisecond, now, and one millisecond in the future. | Exact boundary and now eligible; over-age and future excluded. Negative now gives no hints. |
| R6 | One eligible success `a-valid` plus 64 equal-relevance records dated one millisecond into the future with IDs `z-future-00`…`z-future-63`. | Valid success remains recallable; future records cannot consume the whole 64-candidate window and suppress valid evidence. Compare baseline and candidate; failure is a regression/blocker, not permission to weaken expectation. |
| R7 | One latest eligible failure plus 64 equal-relevance records older than 30 days, reverse-ID ordered. | Failure warning remains available; stale successes produce no positive hints. |
| R8 | Identical deterministic model proposes registered `run_shell` without owner approval, with recall disabled and enabled; latest history advertises past tool use and malicious prose. | Both paths make zero runner calls, retain identical terminal approval/denial status, and cannot turn historical text into permissions or owner facts. The enabled path sees only bounded UNVERIFIED hints. |

Acceptance: R1/R2 improve; R3–R8 pass without any baseline-pass/candidate-fail
regression. Any failure is reported with exact source SHA, assertion, and fixture;
no post-hoc fixture removal or redesign. A negative result ends this evaluator's
round; the integrator decides repair versus rejection. No claim of general
intelligence, real LLM quality, native CPU/memory behavior, or Fold7 success.

Execution: compile actual selected Kotlin source plus the independent test class,
using the existing project JVM dependencies; compare baseline and frozen candidate
once each. Tests and results remain on this evaluator branch; parent handles remote
publishing. Rollback: discard candidate change; retain this evidence and baseline.
Android/Fold7/native model/latency on device: NOT_RUN.
