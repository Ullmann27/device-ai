# Round 2: existing backlog dependency lookup

**Final status: RETURNED_TO_R1 / CANDIDATES_NOT_ADOPTED.** Root declined both
general helper changes after the complete comparisons. The active production
module is byte-identical to R1 / the R2 base, preserving the accepted linear
`nextEligible` selection. Seven new regression tests, both candidate sources
and patches, all raw results and the one environment abort remain archived.

## Bound scope

Archived candidate B: replace the repeated linear `doc.tasks.find` inside private
`depsDone` with exponential range expansion from index zero, followed by binary
search inside that range. The initial pure-binary candidate A is rejected and
archived. No map, persistent cache, schema, public API, mutation
operation, lease, lock, permission or offline-control change. Related existing
experiments: EXP-01 and EXP-11. Round-1 sources/evidence are not rewritten.

The input contract remains valid canonical backlogs, with at most 500 tasks,
strictly sorted unique task IDs, known sorted dependency IDs and no cycles.
The code retains READY → dependencies in declared order → attempt budget and
the existing caller's filter order. It reads the current matching task's state;
no result survives a selection call. Empty dependency lists do not execute a
search. All prior claim errors and completion-evidence checks remain in place.

The shared helper is also used by `claim` and `claimNext`. The only operation
that changes the task array is `propose`; it sorts immediately after insertion.
`applyOps` calls `applyOp` for every step, and each changed result is validated
before the next step. `migrateV1` starts from an empty document and uses those
same proposal steps. There is no separate upsert handler; `propose(ifAbsent)`
returns an unchanged existing document. The file store decodes and validates
documents before operations. No new precondition was added to valid operation
batches. Invalid sparse, unsorted or accessor-backed inputs are not legitimate
canonical documents; existing document validation remains unchanged.

## Baseline and source provenance

- Logical round-2 base: `db3295d51c769afdaf809ac1dfae2ada84a3274f`.
- Public identical-tree source: `04b46f125d1086e6ea540d3c68700407f340f607` (R1 / PR #511).
- Required tree: `d753188fe0e67f6c6f098db6b7073ea199e05530`.
- Baseline module SHA-256: `d0cb68413ff89063b685395c0e633a10c04f038e8329658c085ce0c8748f3a1d`.
- Branch: `tuning/workers-r2-59ad`.

The runner prefers the public commit and falls back to the local logical base
only if the public object is unavailable. It requires both the expected tree
and the exact baseline module hash, imports the full module returned by
`git show`, and records the actual `resolvedCommit`. Therefore the public
reproduction does not require the locally created base commit. Candidate and
harness/fixture hashes are recorded alongside every parity/timing result.

## Functional verification

The initial sparse checkout omitted `app/src/test/resources/continuum/backlog`.
The first suite attempt reported 44 pass / 8 fail, all eight failures reporting
missing versioned fixture files. This environment attempt is preserved as
[`tests-environment-initial.txt`](tests-environment-initial.txt); it is not
presented as a product regression. Only that small versioned fixture directory
was added to the own sparse worktree, with no fixture modifications.

The production module was then restored from the exact R2 base for the complete
baseline test run, which finished before the candidate patch was reapplied.
`source-before.txt` and `source-after.txt` bind those production snapshots.

```sh
node --test --test-reporter=spec .github/continuum/autonomous-teamleader.test.mjs .github/continuum/development-backlog.test.mjs .github/continuum/development-backlog-dependencies.test.mjs
node tools/tuning-workers-r2-20261104/benchmark.mjs --candidate=binary --parity-only
node tools/tuning-workers-r2-20261104/benchmark.mjs --candidate=exponential --parity-only
```

- Baseline: **57/57 PASS**, zero failures/cancellations/skips.
- Candidate: **57/57 PASS**, zero failures/cancellations/skips.
- Full source-bound selector parity: **960 cases PASS**, exact object identity
  and unchanged deeply frozen valid documents.
- Between-call state/budget/object mutation parity: **27 cases PASS**.
- Batch, migration and invalid-document parity: **19 cases PASS**.

For candidate B, the same original 57 tests remain, plus two correctness tests
for search boundaries and admission failures. Both the exact baseline and B
passed **59/59**, with zero failures/cancellations/skips. Final source-bound
parity preserves the same 960 + 27 + 19 cases and adds **219 position-boundary
cases**, all passing. They cover first/last positions and positions immediately
before/at/after powers of two across document sizes through 500. Missing
dependencies cannot occur in a valid canonical document; unknown-dependency
proposals and missing claim targets are checked as rejected operations on a
valid unchanged document, alongside the existing missing-dependency document
validation test.

Candidate B source SHA-256:
`7d807cbceb56ca56c12da2eca5466f0ad56aea1f1c0e86538e275d259c90b89d`.
Its full patch, source receipt, extended before/after tests and parity result
are separate `*-exponential*` / `tests-before-final.txt` artifacts. The initial
A artifacts are not overwritten.

The five new tests cover current-state rereads, replacement dependency objects,
dependency short circuit before exhausted budgets, sorted insertion despite
out-of-order IDs, idempotent proposals, completion before `claimNext`, migration,
claim error precedence and unchanged evidence/transition refusals. Existing
conformance, locking, concurrent-writer and interruption suites remain included.

## Fixed measurement plan

The first complete run is archived as `benchmark-binary.json`, with the exact
production source in `binary-candidate-source.mjs` and its unchanged patch in
`candidate-initial.patch`. All 40 cases / 360 pairs passed the harness checks;
one-minute host load ranged from 6.56 to 6.68. **The pure binary-search candidate
is rejected**, because the dependency-at-index-zero control regressed
consistently: paired factors 0.594 / 0.395 / 0.308 at 20 / 100 / 500 tasks.
At 500 tasks its medians were 40.5772 → 135.2141 µs and its ID reads rose from
499 to 3,992. Those regressions outweigh accepting this candidate merely for
its favorable dependency-heavy cases (500 tasks / 16 dependencies:
31,840.1187 → 1,040.3555 µs, factor 30.299, reads 2,054,096 → 33,396).

Root authorized exactly one general follow-up: exponential range expansion
starting at index zero, then binary search within that range, without a tuned
prefix threshold, index or cache. The same 40 cases and timing parameters must
be retained. Functional boundary tests will be extended before its final run.
No repeated timing of the unchanged candidate or favorable-sample selection is
allowed. The initial source and all raw evidence remain unchanged.

### Candidate B: exactly one environment abort and one successful recovery

The authorized final attempt exited **1** at the unchanged load guard:
`load 17.8 exceeds 9`. Stderr is retained verbatim from the tool result in
`benchmark-exponential-aborted.stderr.txt`; stdout was verified as **0 bytes**
and is retained in `benchmark-exponential-aborted.stdout`. No complete JSON
measurement matrix was emitted. The guard is at case entry; the buffered runner
does not identify any completed prefix in this failed output, so no partial
timing results are inferred.

The aborted attempt was **PERFORMANCE_UNVERIFIED**; its historical receipt is
kept unchanged. Root later issued exactly one `RETRY_START` below the load
limit. The unchanged source, fixtures, timing/counting code and guard were
checked before execution. This one recovery completed with **exit 0**, all
**40 cases / 360 pairs**, and zero stderr bytes. See `retry-execution.json` and
`benchmark-exponential.json`. Timing-phase load was **6.85–7.09**. No additional
attempt, third candidate, changed threshold or favorable-sample repeat occurred.

All 40 rows are in [EXPONENTIAL-RESULTS.md](EXPONENTIAL-RESULTS.md). Index-zero
controls improve and large late/dense dependencies benefit. However, 20-task
`one-middle` regresses from **4.2628 to 6.1476 µs** (paired factor **0.712**), and
100-task `sixteen-first-blocked` from **14.4223 to 22.3031 µs** (factor **0.646**,
ID reads 504 → 588). Both disadvantages occur in **9/9 pairs**. Actual workload
frequencies are unknown; Root declined B as a general helper and restored R1.

### Explicit archive routing

The current runner requires `--candidate=binary` or `--candidate=exponential`.
It reads the chosen full archived module, enforces its exact SHA-256 and
imports that source. It cannot silently load restored R1 as candidate B.
Public-first baseline and tree/hash guards remain unchanged. Original measured
harnesses are retained as `measured-binary-harness.mjs` and
`measured-exponential-harness.mjs`, with hashes matching their raw results.
They are provenance snapshots; the working entry point is under `tools/`.

Only archive routing/metadata changed after measurement. The full timing and
counting tail and 40-fixture module remain byte-identical. The bot verifies
source hashes, syntax and Git after the restore; Root performs the independent
functional archive-route check. No additional timing is authorized this round.

```sh
node tools/tuning-workers-r2-20261104/benchmark.mjs --candidate=exponential
```

All 40 size/workload combinations are retained: 0/1/20/100/500 tasks, no
dependencies, no-dependency/no-match control, one dependency at the first,
middle or last position, 16 spread dependencies, and the first or last of those
dependencies unfinished. At sizes 0/1, a valid inter-task dependency cannot be
formed; those rows are explicitly degenerate no-dependency controls. The
near-front dependency is an intentional negative control for binary search.

Each case uses the same validated document for baseline and candidate, equal
work-based batch sizes and warmups, then nine alternating A/B and B/A pairs.
The batch size is fixed from input search work before timing, not calibrated
from a favorable time. It stays between 8 and 20,000 calls. The one-minute load
limit is 9.0, recorded before and after every case; all raw samples remain.
No IO or document validation runs inside the measured loop.

Only after all timings, temporary getters count task-ID reads and compare the
complete task/dependency state-read sequence. This instrumentation cannot alter
earlier timed property-access feedback. These are work counts, not a claim of
measured total memory allocation. The candidate builds no index or cache.

The measurements are a synthetic Node host selector comparison. They exclude
persistence, validation and task execution. No Android/Fold7, battery, GC,
overall worker throughput, model quality or frontier-model claim follows.

## Reversal and next step

The candidate has no data migration and can be reverted by restoring the
previous `depsDone` implementation. Nothing requires user-file deletion.
The timing command documents reproducibility only. This round is closed;
preserve the raw files if a later separately coordinated reproduction is
requested. Root publishes the archive and tests through PR #511. No direct
push, main/production merge, release or APK operation occurred.
