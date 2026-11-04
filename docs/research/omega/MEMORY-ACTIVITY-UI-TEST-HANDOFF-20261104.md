# Actual Activity memory recall UI test candidate

Base: `9d1b6ddf2ba0fe74740babc2da5f48262180bb6d`.
Branch: `test/continuum-memory-activity-lifecycle-20261104`.
New test: `app/src/uiTest/java/at/heinz/deviceai/MemoryRecallActivityUiTest.kt`.
**Status: NOT_RUN. No production edits or Gradle process started by this subtask.**

The two Robolectric cases use SDK 28, LEGACY graphics and a PAUSED main looper:

1. Construct and attach the actual `AgentActivity` through
   `Robolectric.buildActivity(...).get()`. Verify its compiler remains lazy until
   the real private getter is invoked. Call the real `subscribeMemoryEvents()`,
   compile synthetic memory through the Activity-owned compiler, and verify the
   real per-directory hub/adapter/mapper reaches the Activity's own cockpit bus
   and real orb presentation state only after the Android main-looper flush.
2. Invoke the exact cleanup closures registered in `cockpitSubscriptions`, while
   leaving the renderer attached. Verify a queued flush is suppressed, compiling
   while unsubscribed stays silent, and invoking the real subscription method
   again reconnects the same Activity-owned compiler to current presentation.

Only the renderer registration and event recorder are supplied by the test.
It does not replace the compiler, publisher, hub, factory, mapper or Handler.
It invokes no full `onCreate`, `onDestroy`, chat/model/voice startup, camera,
network or APK operation. Therefore it must not be reported as a complete launch,
full Activity lifecycle, chat, face/audio/GPU or physical Fold7 acceptance.
Cleanup closes the actual subscription handles, drains their detached callbacks,
joins the real memory dispatcher with a bound and stops the three Activity
executors. No unrelated `lateinit` field is fabricated to make startup pass.

Run only after the parent's combined unit/lint gate, under its shared build lock:

```bash
./gradlew :app:testDebugUnitTest --no-daemon \
  -PcontinuumUiWidgetTests=true \
  --tests at.heinz.deviceai.MemoryRecallActivityUiTest
```

The source-set flag is required; ordinary unit runs do not include `src/uiTest`.
Record the actual combined commit, compiler/result logs and JUnit XML before
changing this candidate's `NOT_RUN` status. No success is inferred from source
inspection or the earlier non-Android integration tests.
