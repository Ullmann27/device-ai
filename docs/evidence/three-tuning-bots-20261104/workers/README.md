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

The independent parity harness imports the complete baseline module from `git show e101e393dd6c80803ac9d9ac91fccd9812c4a18d:.github/continuum/development-backlog.mjs`, and imports the actual candidate module. It compares **30,720 cases**, all passing exact object identity, on validated, deeply frozen documents of 0, 1, 20, 100 and 500 tasks. Cases combine mixed priorities, tied priority/date, selective eligibility, two deterministic seeds, and the full Cartesian product of four choices for each of the five optional filters. Canonical input bytes remain unchanged. Source SHA-256 values are recorded in [`parity.json`](parity.json).

## Timing protocol

Formal timing is pending the coordinator's exclusive benchmark slot. Do not treat this checkpoint as a measured speedup.

```sh
node tools/tuning-workers-20261104/benchmark-backlog-selection.mjs > docs/evidence/three-tuning-bots-20261104/workers/benchmark.json
```

The harness uses the same source-bound baseline and candidate, validates fixtures outside the timed loops, warms both versions with 5,000 calls per case, then runs 11 paired rounds with alternating order. It records raw samples, medians, quartiles, paired speedup and host load, and refuses timing above one-minute load 9.0. Sizes stay within the real 500-task limit. Independent comparison counts use temporary read-only priority accessors outside all timed loops: two reads per comparator evaluation in both actual selectors. This instrumentation does not change production code or timing fixtures.

This is a selector microbenchmark with synthetic valid tasks. It excludes persistence, document validation and actual task execution. Dependency lookup work is unchanged; the selector is not the entire worker. Wall time under a shared host, memory/GC on Android, battery use, Fold7 latency and overall worker throughput remain unmeasured.

## Compatibility and rollback

No data migration or API change is needed. Existing persisted backlog documents and callers remain compatible. Revert the tuning commit to restore the previous selector and accompanying tests/evidence; no user data needs to be changed or deleted. No merge into main/production, release, APK build or install was performed.

Status at this checkpoint: source and host functional parity verified; timing pending; publication handled by root. No claim of frontier-model superiority.
