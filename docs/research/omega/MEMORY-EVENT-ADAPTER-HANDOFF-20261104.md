# Actual memory event source for cockpit integration

Status: `SOURCE_INTEGRATED` on the isolated candidate branch only. Host-tested;
Activity activation, complete combined Android gate and device acceptance remain
`NOT_RUN`. No APK, release, installation or model promotion was performed.

## Source and ownership

- Branch: `feat/continuum-memory-events-20261104`.
- Base: `ca31eb8b24b65d084750f0ea10290dc0d6be6733` of the parent memory/brain workstream.
- Original compiled app source used by the focused harness:
  `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`; app runtime JAR SHA-256
  `7b7a2213d3e4a5ba88efffaa13a1261a6923eb11f8fdc4943ad1a2de0d398316`.
- New production file: `app/src/main/java/at/heinz/deviceai/cockpit/ContinuumMemoryEventSource.kt`.
- New test file: `app/src/test/java/at/heinz/deviceai/cockpit/ContinuumMemoryEventSourceIntegrationTest.kt`.
- No change to `MemoryEventSource.kt`, `AgentActivity.kt`, learning/recovery,
  model weights, voice, UI or avatar renderer files. Their current owners keep
  the Activity hook and production event producers.
- Relevant existing architecture: EXP-11 local orchestration, existing learning
  hub and cockpit contracts. No experiment status or memory schema is promoted.

Candidate file hashes and the exact commands/classpaths are preserved under
`docs/evidence/memory-events-20261104/`. The parent integrator records the eventual
published commit and combined results; this subtask itself is `LOCAL_ONLY`.

## Exact integration call and lifecycle

Reuse the existing Activity-owned mapper and the **same app directory as the
actual memory producers**:

```kotlin
val subscription = ContinuumMemoryEventSource.forApp(appDirectory).subscribe(mapper)
```

For screen replacement/destruction, on the UI lifecycle thread:

```kotlin
mapper.detach()
subscription.close()
```

The source also accepts an existing `ContinuumMemoryEvents` in its public
constructor. It does not create another event bus or storage layer. The factory
uses `ContinuumMemoryEvents.forApp(appDirectory)` and its existing canonical-path
identity. The source creates independent subscriptions; each unregisters once.

The adapter serializes sink invocation with close. After close returns no new
sink call can start; an already executing callback on another thread has finished.
Closing from the current callback is supported. Sinks must remain short and must
not wait for a thread that is itself calling close; the existing mapper fits this
contract. A closed subscription releases its sink reference. Listener exceptions
remain isolated by the existing real hub.

The mapper owns delayed UI flushes, so **source close alone cannot cancel a flush
already queued in the mapper**. `mapper.detach()` first is part of the integration
contract and is tested. Host tests execute the queued flush on the test thread;
they do not claim Android Looper or physical rendering acceptance.

## Data and truthfulness boundaries

Only existing ID/hash/status/count/score fields cross this adapter. IDs and kinds
must be nonempty ASCII metadata tokens of at most 128 characters (`A-Z`, `a-z`,
digits, `-`, `_`, `.`, `:`); malformed metadata events are dropped. Query hashes
must be the existing 64-character lowercase hexadecimal format. These checks do
not authorize a producer to place owner text inside an otherwise valid token.
No memory text, query text, hit text, stack traces or new event logs are emitted.

Nonnegative integer counts remain the actual supplied counts; negative document
counts are presented as zero, matching the existing mapper. No arbitrary count
cap is advertised as an exact result. Scores retain their original finite,
nonnegative value, including legitimate BM25 scores above one. NaN, infinities
and negative scores become unknown (`null`), never confidence. Rank 1 identifies
the top result, with at most the first 256 hit records inspected; missing rank 1
means an unknown top score. No numerical overflow-prone aggregation is added.

The UI must preserve `REVOKED`, `UNCONFIRMED`, `HYPOTHETICAL`, zero-hit and zero-entry
semantics. An activity signal is not proof of a confirmed fact or successful
learning. This adapter does not synthesize events from timers, animations, user
text or merely starting research.

## Executed evidence

Toolchain: actual cached Kotlin 2.4.0 compiler, JDK 17, JUnit 4.13.2. The focused
harness overlays the new source/test onto the frozen real Android-compiled app
and test classes; no replacement hub/retriever/mapper is used in integration
checks. A retained-listener test double is used only to force close races that
normal removal would otherwise hide.

| Run | Compile | JUnit result | Meaning |
| --- | --- | --- | --- |
| `events-red` | PASS | 10 tests, 10 expected assertion failures | Adapter did not yet exist; no compile-error RED |
| `events-green` | PASS | 15 tests, 0 failures | 10 new integration tests plus 5 unchanged source/queue tests |
| `events-close-mutation` | PASS | 15 tests, 1 expected failure | Deliberately removing delivery serialization is rejected by the close-race test |

The mutation is a scratch-only negative control, not a shipped alternative. It
retains the closed flag and disables only synchronization of delivery against
close. This demonstrates that a flag-only implementation is not sufficient.

The two warning records in GREEN are expected from the test that deliberately
throws `IllegalStateException` in one listener. The real hub logs only its class
name and continues dispatch to the healthy subscriber, which receives both events.

Covered behavior: real hub document/storage dispatch, coalesced main-thread
mapping, all four memory status categories across scenarios, actual retriever
queries producing salted hashes and zero-hit outcomes, app-directory isolation,
captured CopyOnWrite listener snapshots after close, concurrent in-flight
delivery/close, reentrant and idempotent close, failing-listener isolation,
nonfinite/negative score handling, rank ordering, malformed text metadata, and
the already-posted mapper flush on detach.

The real retriever test calls the existing event-emitting retriever directly.
It proves that path, **not that the current Activity/cognitive compiler invokes
it**. At this base, `MemoryEventSources.production` still equals `null` and the
main cognitive path lacks this producer hook. The parent is adding the separate
actual context-recall publisher; visual integration must activate both producer
and source against its accepted current Activity and then test the combined path.
Storage/document hub events in this test are synthetic payloads sent through the
real production hub, not evidence of a recovered ledger or document ingestion.

Latest integration coordination from the parent: visual PR #491 now has a direct
six-line `MemoryEventSources.forApp(File)` bridge in its existing
`cockpit/MemoryEventSource.kt`, and its Activity calls that factory (local
integration `41433ff0`, upstream implementation `b44f8fa5`). Those sources were
not edited here. During the agreed combined integration, replace that factory's
body with delegation to `ContinuumMemoryEventSource.forApp(appDirectory)` so the
existing Activity call receives this hardened adapter and there is only one
effective bridge. Preserve the existing deprecated `production = null` contract
if upstream still needs it. Recheck exact upstream source/head before editing;
the parent's coordination report is not a combined test result.

## Repeat and remaining gate

The exact executed focused commands are in each run's `provenance.json`. With a
configured complete Android toolchain, the corresponding ordinary project test
command is:

```bash
./gradlew :app:testDebugUnitTest --no-daemon \
  --tests at.heinz.deviceai.cockpit.ContinuumMemoryEventSourceIntegrationTest \
  --tests at.heinz.deviceai.cockpit.MemoryEventSourceTest \
  --tests at.heinz.deviceai.learning.ContinuumMemoryEventQueueTest
```

That new-worktree Gradle command, the full combined suite, Android lint, final
Activity hook, physical Fold7 rendering/audio/lifecycle and signed update remain
`NOT_RUN` for this subtask. The parent owns the serialized combined verification;
do not launch competing Gradle processes. No complete APK may be claimed from
these host checks. Rollback consists of removing the new adapter and its Activity
hook, once added, while leaving existing stores, producer contracts and weights
unchanged.
