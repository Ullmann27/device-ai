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

## Final decision: round 1 retained

**`COMPLETE / ROUND_2_CANDIDATES_NOT_ADOPTED / ROUND_1_PRODUCTION_RETAINED`.**

All three helpers completed the bounded round. The LM and context helpers each tested one candidate. The worker helper tested its initial binary lookup and exactly one principled follow-up using exponential bounds before binary lookup. All four experimental implementations remain archived, but none is adopted as a general production change. The measured gains come with unfavorable controls or uncertain CPU tradeoffs; actual workload frequencies and device behavior were not established.

The final round-2 diff contains regression tests, reproducible experiment tools, raw evidence and handoffs. It contains **no change under `app/src/main` and no change to the production backlog module compared with R1**. The accepted R1 greedy maximum scan, incremental context byte counter and linear `nextEligible` selection remain active. This decision does not withdraw those first-round changes.

| Active production source | SHA-256, equal to R1 |
| --- | --- |
| `app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt` | `d9fc7493360e526323505ff1637981a6e024f6c61a47e4d6f24df22d248df65d` |
| `app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt` | `2100c6d1b4ecd0e2398ef4039266d9456cc33a69b4ea3700b5e4b79b9c7a5a13` |
| `.github/continuum/development-backlog.mjs` | `d0cb68413ff89063b685395c0e633a10c04f038e8329658c085ce0c8748f3a1d` |

### Inference: fewer boxed weights, no established CPU benefit

The primitive `DoubleArray` candidate reduced allocation in all ten positive/NaN-temperature scenarios. The eight greedy controls retained identical allocation. The final CPU medians were nevertheless higher in all ten affected scenarios.

At vocabulary 512, temperature 1 and top-k 50, allocation fell from **15,824.055 to 14,800.055 bytes per call**, while thread CPU rose from **62.247 to 63.039 microseconds**. At vocabulary 2,048 with full top-k, allocation fell from **137,411.146 to 96,395.146 bytes**, while CPU rose from **328.416 to 366.617 microseconds (+11.63%)**.

The preceding identical-R1 control already showed CPU differences from -9.97% to +6.78% in these scenario classes. The candidate results do not prove a device regression, but they also do not establish a sufficiently clear general improvement. No second candidate or extra timing round was used to seek a favorable result.

Exactly two timing processes each contain 18 scenarios, nine alternating pairs and **324 raw batch records**. The single JVM perfdata-lock warning before AFTER metadata remains verbatim. The strict parser accepts that specific startup warning and rejects unknown output, missing observations and mismatched checksums. Root recomputed the committed summary identically from all raw records.

Complete evidence: [inference results](../../evidence/three-tuning-bots-r2-20261104/inference/RESULTS.md) and [inference handoff](TUNING-INFERENCE-R2-20261104.md).

### Context: allocation savings with secret-rich CPU regressions

Reusing exactly the existing two regex patterns preserved the replacement rules, order, output and per-call matching state. Four planned cases combined two inputs with reused or fresh compiler instances. Allocation was lower in **all 48 hot measurement pairs**.

| Input / instance lifetime | Allocated bytes, R1 to candidate | Thread CPU, R1 to candidate |
| --- | ---: | ---: |
| No secret matches / reused | 14,424 to 7,160 (-50.36%) | 11.589 to 9.305 microseconds |
| No secret matches / fresh | 14,448 to 7,184 (-50.28%) | 15.975 to 12.329 microseconds |
| Many secret matches / reused | 197,840 to 170,208 (-13.97%) | 279.007 to 392.124 microseconds (+40.54%) |
| Many secret matches / fresh | 197,680 to 170,008 (-14.00%) | 253.774 to 328.781 microseconds (+29.56%) |

Both secret-rich cases had higher CPU in **11 of 12 pairs**. Their wall-time medians also increased. These findings are retained without attributing them to an unisolated JIT or host effect. They prevent general adoption despite the allocation savings.

Exactly three exploratory fresh-JVM pairs measured first construction plus first compile. The candidate allocated **1,464 additional bytes in each pair**. Time ranges overlapped; there is no established startup-speed or retained-heap claim. No follow-up candidate or timing was performed.

Complete evidence: [context handoff](TUNING-MEMORY-R2-20261104.md), [all raw/derived metrics](../../evidence/three-tuning-bots-r2-20261104/memory/summary.json), and [archive reproduction](../../../tools/tuning-memory-r2-20261104/README.md).

### Worker: substantial large-list gains with remaining control regressions

Both candidates preserve the validated, sorted, unique-ID document contract and read current dependency states. They add no cache or index. Each complete measurement contains the same **40 cases and 360 alternating pairs**, across 0/1/20/100/500 tasks. Task-ID/state instrumentation was performed only after all timed loops.

Candidate A's binary search regressed for dependencies at index zero. At 500 tasks, the separate time medians rose from **40.5772 to 135.2141 microseconds**, with ID reads increasing from **499 to 3,992**. The candidate was rejected despite large gains for dependencies near the end.

The one authorized follow-up, candidate B, starts with exponential bounds from index zero. It improved the initial near-front case and large late/dependency-heavy cases. At 500 tasks with 16 spread dependencies, medians fell from **26,496.3624 to 2,499.5468 microseconds**; the median paired speedup was **10.792**. ID reads fell from **2,054,096 to 123,420**.

However, B retained two consistent disadvantages:

- 20 tasks, one middle dependency: **4.2628 to 6.1476 microseconds**, slower in **9/9 pairs**.
- 100 tasks, first of 16 dependencies unfinished: **14.4223 to 22.3031 microseconds**, slower in **9/9 pairs**, with ID reads increasing from 504 to 588.

These are host selector results. Paired factors need not equal ratios of separate medians. Absolute times from A and B's separate processes are not directly comparable. Real workload frequencies are unknown, so a weighted whole-worker gain is unverified and B is not adopted as the general helper.

B had **exactly one environmental abort** at the unchanged one-minute load guard: 17.8 exceeded 9.0. Its zero-byte stdout, stderr and historical unverified receipt remain intact. Root authorized one unchanged recovery after load fell; it completed all 40 cases with timing-phase load 6.85–7.09 and zero stderr bytes. No third candidate, lowered guard or additional measurement was introduced.

Complete evidence: [worker protocol and all attempts](../../evidence/three-tuning-bots-r2-20261104/workers/README.md), [all 40 B results](../../evidence/three-tuning-bots-r2-20261104/workers/EXPONENTIAL-RESULTS.md), and [worker handoff](TUNING-WORKERS-R2-20261104.md).

## Independent final verification

Root independently executed the final archive routes after all timing windows. It did not repeat the performance measurements. Each runner selects the public R1 baseline first, validates its shared tree, and records the local fallback when the public commit object is absent. Every candidate is read from its full, hash-pinned archive; restored R1 is never silently relabeled as the candidate.

| Scope | Independently executed final check | Result |
| --- | --- | --- |
| LM | R1 and archived primitive-array candidate | 10/10 JUnit methods on each |
| Context | R1 and archived pattern-reuse candidate | 27/27 JUnit methods on each |
| Context direct comparisons | Complete outputs, UTF-8 bytes, ordering, exceptions and clock effects; fresh/reused lifecycle checks | 462 + 128 PASS |
| Worker active source | Existing teamleader/backlog suites plus seven new regression methods | 59/59 PASS; zero failed, skipped or cancelled |
| Worker archived A | Exact source import, selection/state/operation/position parity | 960 + 27 + 19 + 219 PASS |
| Worker archived B | Same final archive route and complete parity matrix | 960 + 27 + 19 + 219 PASS |

There are **96 distinct test methods in these scopes, including 18 new regression methods**. Baseline/candidate repetitions and loops inside a method are not counted as additional methods. The LM tests include 38,344 direct sampling cases and 130 Unicode/model token comparisons per implementation; the additional sum-bit/RNG checks are detailed in its own handoff. Context tests include concurrent distinct inputs and the existing Unicode, budget and ordering coverage.

Root also checked the three unchanged active source hashes, all archived candidate identities, original worker harness hashes, identical timing/counting tails and fixture hashes, complete raw record matrices, and derived medians. Its verification files preserve the actual outputs and source receipts: [coordinator verification](../../evidence/three-tuning-bots-r2-20261104/coordinator/verification.json).

The worker's initial missing-fixture test attempt remains archived separately. Adding its already-versioned fixture directory resolved that environment omission. Raw JVM warnings and patch-context whitespace are retained rather than edited to make evidence look cleaner.

## Existing redaction edge case for the separate repair owner

The new context regression confirms the same existing behavior in R1 and the archived candidate:

```text
Input:  authorization=Bearer abcdefgh
Output: authorization=[REDACTED] abcdefgh
```

The assignment rule consumes `Bearer`, leaving the following token text. This is a reproducible preexisting limitation, not introduced by the tuning experiment. The pure-tuning task did not change redaction rules, modify another team's branch or send an external repair message. The concrete reproducer is now available for the isolated repair mechanism; complete secret removal must not be claimed for this input.

## Publication and limits

The three completed archive/test packages were published to the existing draft branch, with exact local/remote Git-tree equality and expected-head leases:

| Package | Published commit | Local equivalent |
| --- | --- | --- |
| LM | `adbcaf7386dcddabb7618d0e669100b9a5fed1e0` | `ff6555efd92a2a7913495497d5d2548b40d975db` |
| Context | `b8eedf07554adc04a545cabf2539006d4ca0c114` | `a62b72564cb79ddf334b183a5a9b9760372a3a5f` |
| Worker | `1f9ffb8f9ea03bc2ea1ae483baddf3a26fd0b395` | `827e97e7bd516b2d6757062b0ea63ea3a4d6b5de` |

This final handoff and the independent verification logs follow those packages in the same draft PR. All retained observations concern the specified host components. Full Gradle/lint/APK acceptance, ART/Fold7 measurements, battery/thermal behavior, model-answer quality and frontier comparisons were not executed. No main/production merge, installation, model promotion or permanent background execution is part of this completed round.
