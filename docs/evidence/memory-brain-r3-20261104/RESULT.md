# Memory / brain R3 — executed repair and Android results

## Current acceptance result

The two bounded memory repairs pass their unchanged regressions, including all
14 new cases in the actual Android project. The complete Android unit suite is
**FAIL: 2,638 tests, 2,618 passed, 1 failed, 19 skipped**. Its only failing case is
`ContinuumLandscapeCockpitLayoutTest.everythingFitsOneScreenWithUsableTargetsAndNoOverlap`,
with `832.0 x 700.0 font 1.0 VIEW_AVATAR ≥ 48 dp`. This failure is preserved;
the full suite is not accepted and no original assertion was weakened.

A subsequent separate run on the same unchanged source passed both actual
`MemoryRecallActivityUiTest` cases and `:app:lintDebug`, exit 0. This repository
configures lint with **checkOnly = NewApi**; its 0 reported issues do not mean
that every lint rule was enabled. Production and test compilation succeeded.

Tested local commit: `ec2543bef908d7d346a9767f9b095e84254090d1`.
Source-equivalent published commit: `3704b62585c6ce6881ab0dfe0c9947cddd1531c2`.
Their identical full tree is `ed4984ba9ac873e081a66d7a7be871f029ef5be5`;
the app subtree is `befaef97b94b38ebeba65c0160e332d747dafd7a`.
Raw results, complete XML archive, before/after source hashes and execution
limits are in [android-gates/README.md](android-gates/README.md).

The [read-only layout diagnosis](layout-diagnostic/README.md) reproduces the
unchanged original JUnit failure. Float addition/subtraction produces
47.999996185302734375 dp, one representable Float below 48, for the three
presentation controls at that viewport. Nine relevant source/test blobs are
identical to the C1 intake; the latest inspected C1 layout is also unchanged.
This is a numerical contract failure with no physical-device measurement.
The UI owner retains the repair; this specialist patch changes no UI geometry.

The first attempt correctly stopped before Gradle with
`NOT_RUN_LESS_THAN_1_GIB_FREE` / exit 75. Space subsequently recovered and both
reported runs actually executed under C1's existing two shared locks. This
earlier preflight refusal is retained separately and is not counted as a test.

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

The five focused directories contain actual-source JVM runs. They were followed
by the separately recorded Android gates above. The bounded Robolectric gate
constructs and attaches the real Activity and exercises its compiler getter,
subscription closures, memory hub, Android Handler/main looper and orb renderer.
It does not execute full onCreate/onDestroy, native graphics, a real model turn,
microphone, TTS or a physical device. No APK was built. The owner's offline
setting and model weights are unchanged.

The independent compiler review and its limits are in `REVIEW.md`. Original
tests were neither removed nor weakened. The scope leaves #511's tuning,
#505's view/retrieval work and C1's shared Activity entry points with their owners.
