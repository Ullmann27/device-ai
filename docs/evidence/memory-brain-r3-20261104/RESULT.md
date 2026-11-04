# Memory / brain R3 — executed focused repair results

## Source and independent tests

Base C1: public `b04daf5db2cbe40a9fa757499a0d295176a1bfce`, identical complete
tree `aa439516d342a5410d4473275756a4fa85b4c981` to local `e19bf2768dc24b7c2ea1040538fdd661269a540c`.
Tests were committed separately before the repair, now also published at
`33706d166afafe8f93d3b9aa1db34190f950eb68` in #522.
Exact source and test hashes: `source-binding.json` and each run manifest.

## Contradiction privacy and untrusted-text boundary

The actual Activity passes `memories.filter { ... CONTRADICTED ... }.map { it.content }`
alongside the original records. The compiler previously copied these strings
into its mandatory section before normal memory privacy/identity filtering.

- Baseline: initial four cases **2 FAIL / 2 PASS**. The frozen six additional
  policy/alias holdouts were then combined with those same cases: **10 total,
  6 FAIL / 4 PASS**. Do not add the earlier four again.
- Candidate compiler `90b40f69fe6a9c47f4273ea312d99baa380a657c`: **23/23 PASS**,
  comprising the unchanged ten new cases and thirteen existing integrity/compiler
  tests. Kotlin compilation and JUnit both exited 0.
- Frozen independent holdout SHA256:
  `dcf924b4e15b74adf2492ae0b38260bd79dbc19f6787abbcdafe3640cde049f7`.

The compiler snapshots the input collection, resolves identities before either
projection, and conservatively excludes a content-only note if any matching
source has forbidden privacy or an ambiguous identity. Permitted notes and IDs
use the existing full untrusted sanitizer and complete evidence framing. A
generic notice preserves the fact that a conflict remains unresolved while
withholding its content and identifiers. Standalone caller corrections remain
required and atomic; oversized forbidden source text no longer consumes their
budget. Explicitly permitted SENSITIVE evidence remains permitted.

Contract limit: this existing string API can attribute only exact content
projections whose original records remain in `input.memories`. The actual
Activity satisfies this. Unmatched free notes remain explicitly unattributed,
untrusted caller annotations; no origin/privacy inference for arbitrary
rewritten prose is claimed. No Activity or public API change is required.

## Mapper lifecycle and atomic write accounting

Four new unchanged tests use the real mapper, bus and actual producer/flusher
threads. Their test SHA256 is
`3d490f43091ddff69dd264c5b9f48b73613d82c5bfbddcfc11defaf845be3690`.

- Baseline: **4 tests, 3 FAIL / 1 PASS**. Both detach-during-callback cases
  emitted extra events. The fixed 80,000-write concurrent workload accounted
  for only **79,387 writes** in this observed baseline run. Hitting that old
  race is scheduling-dependent; this is the actual observed result, not a
  deterministic performance claim.
- Candidate mapper in the source recorded by `mapper-green/manifest.json`:
  **4/4 PASS**, compiler/JUnit exit 0, including exact 80,000-write accounting.

The last write and its count now share one immutable atomic value. Each later
bus emission checks whether a preceding callback detached the mapper. No mapper
lock spans renderer callbacks, and a bounded callback/producer test guards
against introducing that deadlock. This repairs presentation metadata; it is
not evidence that the old ledger itself lost stored records. There is no new
global cancellation barrier for an event already inside the bus.

## Execution and review boundaries

Raw commands, source hashes, dependency hashes, compiler logs and JUnit output
are retained in the five run directories. The compiler harness extracts the
unchanged RecallHit declaration. The mapper harness omits only the unused
MemoryEventSources factory while keeping the complete actual mapper/interfaces;
its other source files are compiled unchanged. No compiler, retriever, mapper
or bus is replaced by a fake implementation.

These are actual-source JVM runs, not Android Activity, speech, GPU or device
execution. Full current Android/lint and bounded Activity gates are **NOT_RUN
at this checkpoint**, to be recorded separately on the frozen combined source.
No APK was built. The owner’s offline setting and model weights are unchanged.

The independent compiler review and its limits are in `REVIEW.md`. Original
tests were neither removed nor weakened. The scope leaves #511's tuning,
#505's view/retrieval work and C1's shared Activity entry points with their owners.
