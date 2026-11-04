# Compiler-to-orb integration evidence

Base: `cbb9b18b8deee147a68685067bcee2d73956b2bb`.
Branch: `feat/continuum-memory-event-integration-20261104`.
Scope: one new integration test and its evidence; **no production edits**.

## Real path under test

`AiContextCompiler.withRecallObserver(ContinuumMemoryRecallPublisher(appDirectory))`
→ existing per-directory `ContinuumMemoryEventHub`
→ `ContinuumMemoryEventSource.forApp(appDirectory)`
→ existing `MemoryEventMapper` and `CockpitAnimationBus`
→ existing `BrainOrbAnimationRenderer`
→ actual `JarvisSequenceEffects` recall count, brightness and other orb fields.

The tests use the real compiler, retriever, publisher, hub, adapter, mapper and
orb renderer. Only Android's main-thread scheduling is manually pumped on the
test thread. An additional bus observer records fields and thread identity; it
does not manufacture or forward production events. Memory fixtures are synthetic
test inputs, never imported as owner memories. The test calls no hub `emit` and
does not inject memory events directly into the mapper or renderer.

## Four integration checks

1. No events or effects appear merely by subscribing. A real compilation with
   two permitted memories plus sensitive/stale records produces exactly two
   presented hits after the main-thread flush. The event carries a salted hash
   and no fixture text. Real orb count/brightness match the received recall;
   no document flight, confirmed-write pulse or hypothesis effect is invented.
2. After a successful recall lights the orb, a selected memory group that cannot
   fit the context budget produces zero included hits. Its real zero-hit event
   clears the prior recall indication at the next main-thread flush.
3. A mandatory context-budget failure produces no posted event or memory effect.
4. A replaced screen keeps its old renderer subscribed while a recall flush is
   queued. Correct mapper detach/source close suppresses that queued effect;
   a new screen attached to the same actual hub still receives the next recall.

## Executed results

- `memory-orb-chain-green`: all nine production source files in this path were
  compiled fresh against the frozen real Android app/test dependencies.
  **9/9 JUnit tests passed:** four new integration tests and five unchanged
  `BrainOrbSignalsTest` cases.
- `memory-orb-chain-disconnected-red`: a scratch-only negative control disconnects
  the compiler's observer while preserving normal context compilation. It compiles
  successfully but **3/9 tests fail**, specifically the three tests requiring
  actual recall delivery. The failed-compilation test remains green, as expected.
  This mutant is not a production change and is not shipped as an alternative.

These are tests of existing combined production behavior, not a claimed original
feature RED→GREEN cycle. The negative control demonstrates that the new tests
detect a disconnected producer rather than passing solely on renderer fixtures.

Raw logs, exact compile/test commands, source hashes and baseline identity:
`docs/evidence/memory-compiler-orb-20261104/`.
Toolchain: cached Kotlin 2.4.0, JDK 17, JUnit 4.13.2. Compilation reports the
existing hub warning recommending Kotlin `Any` instead of `Object`; no production
source was changed to suppress it. JUnit GREEN has no runtime warnings or errors.

## Boundaries and next gate

This proves the host-level **connected cognitive recall → real orb presentation
state** path. It does not prove Activity construction, Android Looper behavior,
face expression, speech/audio, GPU rendering or physical Fold7 behavior. The
visual integrator retains the Activity hook and its latest UI branch. Rerun this
test on that final combined source; then run the serialized full Android suite
and lint before the authorized final APK gate. No APK, release, installation,
network request, weight edit or publication was performed by this subtask.

Ordinary configured Android project command for this integration check:

```bash
./gradlew :app:testDebugUnitTest --no-daemon \
  --tests at.heinz.deviceai.cockpit.CompiledMemoryOrbIntegrationTest \
  --tests at.heinz.deviceai.ui.BrainOrbSignalsTest
```

That Gradle command is an integration entry point, not a claim it ran in this
subtask. The executed direct compiler/JUnit commands are in the receipts.
