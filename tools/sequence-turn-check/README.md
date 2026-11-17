# Sequence turn lifecycle host check

This fallback compiles **the complete original production files** listed in
`run.sh`, including `JarvisSequenceView`, `JarvisSequenceEffects`,
`BrainOrbAnimationRenderer`, the event bus and contracts. It does not extract,
copy or reimplement their event/turn logic. JUnit drives synthetic events and
asserts on the production effect state. The repository's existing
`BrainOrbSignalsTest` is compiled and run unchanged as a regression control.

The small classes under `fixtures/` stand in only for unavailable Android
platform APIs and `AndroidContinuumPainter`. A compile-only pill-label dependency
also throws if accessed; ToolGate and pill actions are not simulated or tested.
The platform stand-ins permit synchronous event
delivery and an invalidation no-op. Rendering, the frame queue, the real Looper,
Activity/lifecycle, fonts and native resources are **unavailable** and fail if
invoked. No result from this check is Android, Robolectric, visual, performance
or Fold7 acceptance. The actual Android test remains in
`app/src/uiTest/java/at/heinz/deviceai/ui/JarvisSequenceTurnLifecycleUiTest.kt`.
The fixture records every renderer exception before the bus can swallow it;
JUnit checks this error list after every scenario, outside the bus.
The `Handler` stand-in is compile-only: constructing it stores no queue, and any
attempt to call `post` throws. Background completion delivery is therefore
covered only by the real Robolectric cases, never by a simulated host queue.

The lifecycle cases cover completions received synchronously before and during
a motion pause, stale command/worker/timeline callbacks, and unfinished or new
work after the pause. The bounded recent-turn history remains a presentation
cache, not a worker execution ledger. The host check does not simulate queued
background delivery or View detach/reattach; those cases are defined only in the
Robolectric test and require a separate real Android-platform test run.

Run with an existing Kotlin 2.0.20 compiler dependency directory containing
JUnit 4.13.2 and Hamcrest 1.3, for example Gradle 8.11.1's `lib` directory:

```bash
CONTINUUM_KOTLIN_COMPILER_LIB=/path/to/gradle-8.11.1/lib \
  bash tools/sequence-turn-check/run.sh /path/to/fresh-output-directory
```

The output contains compiler and JUnit logs plus hashes of all production,
fixture and harness inputs, verified again after execution. No download,
Gradle task, API request, APK or device action occurs.
