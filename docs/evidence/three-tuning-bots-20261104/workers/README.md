# Existing worker backlog selection tuning

## Scope and source binding

- Base: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d` (PR #491).
- Assigned branch: `tuning/workers-59ad`; integration/publication is coordinated by the root agent through draft PR #511.
- Related existing experiments: **EXP-01** (bounded local workshop), **EXP-11** (orchestration over the canonical backlog). No new experiment or execution capability.
- Only production change: `.github/continuum/development-backlog.mjs`, `nextEligible()`.
- Existing callers: `autonomous-teamleader.mjs` selects owner REPAIR, owner TEST and read-only INVESTIGATE tasks through this function.

The selector now keeps the best eligible task during one pass, instead of materializing and sorting every eligible task to return index zero. The order remains descending priority, ascending creation time, then ascending ID. The original eligibility expression and optional-filter order are preserved. The returned value remains the exact task object from the input, or `null`.

This equivalence is for valid canonical backlog documents: dense arrays, at most 500 tasks, sorted unique IDs, validated scalar fields and acyclic known dependencies. Sparse arrays and objects with side-effectful property getters are not legitimate parsed backlog documents. Validation, schema, mutation operations including `claimNext`, leases, locks, retries, cancellation, owner permissions and offline controls are unchanged.

## Verification

Node `v24.22.1`, V8 `13.6.233.17-node.58`, Linux x64, AMD EPYC 9V74 host with 9 available CPUs. These are host observations, **not Android/Fold7 results**.

```sh
node --test --test-reporter=spec .github/continuum/autonomous-teamleader.test.mjs .github/continuum/development-backlog.test.mjs
node tools/tuning-workers-20261104/benchmark-backlog-selection.mjs --parity-only
git diff --check
```

The original two suites passed 48/48 on the unchanged base in the coordinator's baseline. The candidate passed **52/52**, zero failed, cancelled or skipped. The four added tests cover priority/date/ID ties, exact result identity and frozen inputs, all optional scopes and null subjects, eligibility/filter short-circuit order, and dependency release only after existing completion evidence. Existing conformance, tampered-state, locking, interrupted-work and concurrent-writer tests are retained. Raw output: [`tests.txt`](tests.txt).

The independent parity harness imports the complete baseline module from `git show e101e393dd6c80803ac9d9ac91fccd9812c4a18d:.github/continuum/development-backlog.mjs`, and imports the actual candidate module. The initial standalone parity run passed **30,720 cases** ([`parity.json`](parity.json)); the final harness passed **40,960 cases** ([`benchmark.json`](benchmark.json)). All comparisons require exact object identity on validated, deeply frozen documents of 0, 1, 20, 100 and 500 tasks. Cases combine mixed priorities, tied priority/date, selective eligibility, an explicit no-match control, two deterministic seeds, and the full Cartesian product of four choices for each of the five optional filters. Canonical input bytes remain unchanged. Both source hashes and the final harness hash are recorded in the final JSON.

## Timing protocol

The coordinator staggered the three bots' CPU runs. The final complete comparison passed with exit 0 on 2026-11-04, timing window 19:04:04.613–19:04:16.691 UTC. The observed one-minute host load ranged from **4.35 to 4.56**, below the recorded limit of 9.0. Other host workloads were not controlled.

```sh
node tools/tuning-workers-20261104/benchmark-backlog-selection.mjs > docs/evidence/three-tuning-bots-20261104/workers/benchmark.json
```

The harness uses the same source-bound baseline and candidate, validates fixtures outside the timed loops, warms both versions with 5,000 calls per case, then runs 11 paired rounds with alternating order. Each round runs 250,000 selections for 0/1 tasks, 12,500 for 20 tasks, 2,500 for 100 tasks, or 2,000 for 500 tasks. It records raw samples, medians, quartiles, paired speedup and host load, and refuses timing above one-minute load 9.0. Sizes stay within the real 500-task limit. Independent comparison counts use temporary read-only priority accessors **after every timed case has finished**: two reads per comparator evaluation in both actual selectors. This instrumentation does not change production code or timing fixtures and cannot affect the timed selector's prior type feedback.

### Final results

All times below are median **microseconds per selection**, not whole worker cycles. The factor is the median of the 11 paired baseline/candidate ratios; it is intentionally not calculated by dividing the two separate medians. A factor below 1 favors the baseline. Quartiles and every sample are in [`benchmark.json`](benchmark.json).

| Tasks | Input | Baseline µs | Candidate µs | Paired factor | Comparisons before → after |
|---:|---|---:|---:|---:|---:|
| 0 | Mixed priority | 0.0640 | 0.0386 | 1.657 | 0 → 0 |
| 0 | Ties | 0.0351 | 0.0222 | 1.567 | 0 → 0 |
| 0 | Scoped | 0.0641 | 0.0381 | 1.795 | 0 → 0 |
| 0 | Scoped, no match | 0.0626 | 0.0377 | 1.658 | 0 → 0 |
| 1 | Mixed priority | 0.0725 | 0.0517 | 1.510 | 0 → 0 |
| 1 | Ties | 0.1207 | 0.0787 | 1.480 | 0 → 0 |
| 1 | Scoped | 0.0593 | 0.0386 | 1.667 | 0 → 0 |
| 1 | Scoped, no match | 0.0684 | 0.0436 | 1.549 | 0 → 0 |
| 20 | Mixed priority | 2.5466 | 0.8798 | 2.895 | 61 → 19 |
| 20 | Ties | 1.8561 | 0.9813 | 1.897 | 19 → 19 |
| 20 | Scoped | 1.4067 | 1.0725 | 1.308 | 1 → 1 |
| 20 | Scoped, no match | 0.9831 | 0.9885 | 1.001 | 0 → 0 |
| 100 | Mixed priority | 19.4314 | 4.8423 | 4.009 | 534 → 99 |
| 100 | Ties | 9.0618 | 5.4233 | 1.657 | 99 → 99 |
| 100 | Scoped | 3.3187 | 3.2247 | 1.086 | 21 → 8 |
| 100 | Scoped, no match | 2.6360 | 2.7621 | 0.939 | 0 → 0 |
| 500 | Mixed priority | 81.6812 | 15.4194 | 5.258 | 3,833 → 499 |
| 500 | Ties | 35.5960 | 18.5619 | 1.731 | 499 → 499 |
| 500 | Scoped | 20.1746 | 19.5672 | 0.989 | 206 → 47 |
| 500 | Scoped, no match | 14.1779 | 13.8413 | 0.928 | 0 → 0 |

Mixed-priority inputs have all tasks eligible. Tie inputs have equal priority and creation date. Scoped inputs add non-READY tasks, exhausted budgets, unresolved dependencies and kind/source/base/operation filters; 2, 9 and 48 tasks survive at sizes 20, 100 and 500. The no-match control uses the same filter form with no surviving candidates. At 0/1 tasks both scoped variants also return null.

The deterministic improvement is the removal of the filtered candidate array and full sort: at the 500-task mixed-priority limit, ordering comparisons fall by **86.98%**, from 3,833 to 499. The best-element ordering stage now needs at most one comparison per further survivor. Existing dependency searches are unchanged, so the whole function is not claimed to be linear for dependency-heavy documents.

The mixed-priority and tie cases show a host timing benefit. **There is no demonstrated general speedup for heavily filtered inputs.** In particular, the paired median for the 500-task scoped case is 0.989 and the no-match controls at 100/500 are 0.939/0.928, favoring the baseline. The 100-task no-match medians differ by roughly 0.126 µs. Host variation makes separate medians and paired ratios differ, particularly at 500 tasks; none of these controls are omitted or promoted into a positive claim. The optimization is justified by lower ordering work and the measured many-survivor cases, not a promise of faster every-call execution.

### Retained initial run and method correction

[`benchmark-initial.json`](benchmark-initial.json) preserves the complete first run, including its unfavorable control values. Its original `scoped` fixture accidentally correlated kind and source so that no task could match. That is a valid no-match control, but not a positive scoped-selection workload. In addition, that harness counted comparisons between timed cases using property accessors, which could affect subsequent JIT feedback. The initial run is **preliminary / method-limited** and does not supply the headline figures above.

The final harness retains that original no-match fixture as `scoped-no-match`, adds a genuinely matching scoped fixture, and defers all instrumented comparison counts until all timing is finished. No production code was changed between runs. The initial harness is reproducible from published commit [`963ac4b`](https://github.com/Ullmann27/device-ai/commit/963ac4b37c271380f113469d8610d3937ac10d45) (original local bot commit `0e0ba1b`); the final harness is bound by SHA-256 `d9749f4de4433611cf1496a5112378cf01a5ad8d59ca1c0db9177db58f5c6366` in the final output.

This is a selector microbenchmark with synthetic valid tasks. It excludes persistence, document validation and actual task execution. Dependency lookup work is unchanged; the selector is not the entire worker. Wall time under a shared host, memory/GC on Android, battery use, Fold7 latency and overall worker throughput remain unmeasured.

## Compatibility and rollback

No data migration or API change is needed. Existing persisted backlog documents and callers remain compatible. Revert the tuning commit to restore the previous selector and accompanying tests/evidence; no user data needs to be changed or deleted. No merge into main/production, release, APK build or install was performed.

Status: source, host functional parity and the bounded host comparison are verified. Production source commit: `0e0ba1b`; publication handled by root. Device and end-to-end worker throughput are unmeasured. No claim of frontier-model superiority.
