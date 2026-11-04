# Memory mapper cancellation and counted writes — R3

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

Next gate: execute the frozen tests-first baseline, execute the repaired mapper,
retain the actual counter-race outcome, and run existing mapper/adapter regressions.
Rollback consists of reverting this bounded implementation change; it requires no
database migration, persisted-memory deletion, model change or consent change.
