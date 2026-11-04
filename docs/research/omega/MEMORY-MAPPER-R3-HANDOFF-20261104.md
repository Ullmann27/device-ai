# Memory mapper cancellation and counted writes — R3

## Current root execution and publication

The root combined this isolated patch into #522 and published the repaired
source at `3704b62585c6ce6881ab0dfe0c9947cddd1531c2`, full tree
`ed4984ba9ac873e081a66d7a7be871f029ef5be5` (local tested commit
`ec2543bef908d7d346a9767f9b095e84254090d1`). The root executed the real baseline:
4 tests, 3 FAIL / 1 PASS, with 79,387 of 80,000 writes accounted in the observed
race. The unchanged four tests then passed against the repair, including exact
80,000-write accounting. All four also pass within the actual Android suite.

The real attached Activity/Handler gate passed 2/2; configured NewApi lint
reported 0 issues. The complete Android suite still fails one unchanged
landscape layout assertion: 2,638 tests / 2,618 PASS / 1 FAIL / 19 SKIP. These
overlapping counts are not additive and do not establish device readiness.
See `docs/evidence/memory-brain-r3-20261104/RESULT.md` and `android-gates/`.

The implementation-checkpoint notes below record what the subagent had and had
not run at that earlier point. They are not the current publication status.

## Source and ownership

- Public integration input: C1 `b04daf5db2cbe40a9fa757499a0d295176a1bfce`.
- Locally reconstructed input: `e19bf2768dc24b7c2ea1040538fdd661269a540c`, full tree
  `aa439516d342a5410d4473275756a4fa85b4c981`.
- Own branch: `fix/continuum-memory-mapper-r3-20261104`.
- Tests were committed first at `427e6d444a1a825722b51fc1ef5d57a0b205cdb0`.
- Production scope is only `MemoryEventMapper` inside
  `app/src/main/java/at/heinz/deviceai/cockpit/MemoryEventSource.kt`.
  The interfaces, factory, Activity, event hub, publisher, avatar views and old tests
  are unchanged. No foreign worktree was edited.

## Concrete problems and repair

The existing mapper checked `detached` once at the beginning of a flush. A renderer
could detach it while handling the first document event, yet the same flush still
started storage and recall events. A check immediately before each bus emission
now suppresses remaining event kinds after that reentrant detach. The same rule
applies when detachment happens in the storage callback.

The latest storage event and its count previously occupied separate atomic
variables. A flush could take the old count, a producer could write the next event
and increment its count, and the flush could then consume that next event. A later
flush would find the residual count without an event. The public count of ledger
writes could therefore be wrong even though the ledger itself was unchanged.

One atomic reference now contains both the latest immutable `MemoryStored` event
and its accumulated count. A compare-and-set loop updates the pair; a single
`getAndSet(null)` consumes the pair. Counts saturate at the representable
`Int.MAX_VALUE` instead of wrapping negative. The standard event order, constructor
and sink APIs, latest-event coalescing and 100 ms default scheduling interval are
preserved.

No mapper lock surrounds `bus.emit`. This is necessary because a renderer may
close an adapter while a hub producer holds the adapter's delivery lock and calls
the mapper. Holding a mapper lock across that callback could invert those locks.

This patch prevents the demonstrated reentrant remaining-batch deliveries. It does
not introduce a cross-thread cancellation barrier inside `CockpitAnimationBus` or
change the existing main-thread Activity lifecycle contract.

## New actual-source tests

`MemoryEventMapperLifecycleRegressionTest` contains four tests:

1. Queue all three kinds and detach in the first document callback. Only that first
   event may be emitted. The old source's failure is deterministic by its call order.
2. Queue storage and recall, then detach in the storage callback. Recall must be absent.
3. The renderer waits with a bounded latch for another thread to call the actual
   mapper. The producer must finish while the callback is still active, and both
   writes must arrive as count-one events. This guards against a lock being held
   across renderer callbacks.
4. One actual producer sends a fixed 80,000 writes while a single flush thread
   executes scheduled runnables. After bounded joins and complete queue drain,
   summed emitted counts must equal 80,000 and the final entry must be retained.
   No sleep-based success criterion is used. A particular baseline run may or may
   not hit the old counter race; its observed result must be retained honestly.

## Execution status at the implementation checkpoint

**LOCAL_ONLY / NOT_PUSHED by this subagent. Runtime tests: NOT_RUN_BY_SUBAGENT.**
The root agent owns source publication and its separate offline JVM harness.
The tests-first commit remains available for baseline RED execution against the
unchanged mapper. No production fix was substituted into that baseline commit.

Executed here: `git diff --check` passed, and a byte comparison confirmed that the
factory/interface prefix before `class MemoryEventMapper` is unchanged. No old
assertion was removed or weakened. Source hashes are recorded in
`docs/evidence/memory-mapper-r3-20261104/implementation-manifest.json`.

With a configured Android toolchain, the focused project command is:

```bash
./gradlew --offline --no-daemon --max-workers=1 \
  :app:testDebugUnitTest \
  --tests at.heinz.deviceai.cockpit.MemoryEventMapperLifecycleRegressionTest
```

That command was not run by this subagent. No Gradle/APK, full Activity, native
graphics, microphone, TTS or Fold7 result is claimed. Earlier integration test
counts are not results for this new source.

## Compatibility and handoff

Relevant existing contracts: EXP-06 observation/provenance boundary and EXP-07
independent negative validation. No experiment is promoted or marked complete;
this is an existing presentation-path repair. Root combines this patch with its
separate compiler/privacy work and coordinates final Activity ownership with C1.

Those focused gates have now run through the root as recorded above. Next is
C1's integration and source-bound acceptance of its combined candidate.
Rollback consists of reverting this bounded implementation change; it requires no
database migration, persisted-memory deletion, model change or consent change.
