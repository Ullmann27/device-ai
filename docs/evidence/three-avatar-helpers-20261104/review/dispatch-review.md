# Independent read-only review — JarvisAvatarSlot fanout

Reviewed fix: `b708f553723ec1a94ce3265345c4e0f61b1cbffe`.
Tests-only parent: `4d0b4a6f86dc9f878a8921957fd79b184c421571`.
Result: REVIEW-PASS for the narrow dispatch correction under the existing main-thread renderer contract. No Critical/Important regression identified in the diff. No repository sources changed during this review. Root reported the actual baseline execution as 9 tests / 7 failures / 2 passes; this review did not independently run Gradle.

## Checked behavior

- A failing active registry sink no longer prevents posting the renderer event. Each event reads the current registry sink.
- Every registration, including null and the same renderer instance, replaces a distinct immutable token. Queued work compares that token before invoking the renderer, so replacement/unregister/re-register cannot revive old work.
- The coarse state callback and the full AvatarEvent callback have separate exception boundaries. A coarse exception preserves full expression/gaze delivery when registration remains current.
- A second token check after the coarse callback prevents a synchronous unregister/re-register from receiving the remainder of the old event.
- Public renderer getter, register, addListener and fanOut signatures remain unchanged. The volatile immutable registration safely publishes the renderer/token pair together.
- The nine regression tests exercise the real slot with a manually drained queue rather than source-text matching. Their stop/replace/same-instance scenarios correspond to the defect.

## Real callers and ordering limits

`AgentActivity.kt:256` posts through a Handler bound to the main Looper. Registrations are in UI construction (`buildConversationPane`, line 967) and `onDestroy` (line 686). `ContinuumHomeView`'s registration listener posts a fresh lookup (`applyAvatarSlot`) rather than retaining the listener's renderer argument. `JarvisHoloAvatar.onAvatarState/onAvatarEvent` forward to the hologram and do not synchronously replace the registration. `AvatarDirector` serializes its producer methods with `@Synchronized`. These caller files are byte-identical between the reviewed commit and base `a65cfca` (verified with git diff).

The token check and invocation are not atomic against an arbitrary background-thread register call. Correctness therefore depends on the existing main-thread renderer/registration contract, which the current production callers satisfy. This patch does not provide a new cross-thread registration API.

The registry callback runs before the renderer token is captured. An arbitrary registry implementation that synchronously replaces the renderer or recursively emits events could affect recipient/order selection. No such production registry assignment was found in `app/src/main`; the registry defaults to NoOp. This is an existing behavior outside the narrow queued-work regression, not a newly demonstrated application regression.

## Material integration limit

`AgentActivity.kt:947` immediately replaces the initial fanout sink with `avatarDirector.attach(it)` while constructing the real `ContinuumHologramView`. Subsequent live events use that direct sink. The fanout correction is therefore validated as a dispatch component; it does not establish that the application's normal post-construction speech stream traverses fanout. No wiring change was made or requested in this review.

No device lifecycle, GPU rendering, APK or deployment result is claimed.
