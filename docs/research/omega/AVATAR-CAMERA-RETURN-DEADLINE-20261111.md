# Avatar camera return test deadline

Canonical integration: root, `integration/continuum-cinema-apk53-20261110`, PR #739.
Support branch: `fix/camera-return-deadline-20261111`.
Base: `f495fd633509e11a52519a0bd89d96b22cb91b3b`.

## Observed failure

Root's targeted Android/JVM run on the exact base completed 55 tests: 53 passed, two failed.
Both camera STOP tests sampled `1.4543533E-5` instead of exactly zero. In the lifecycle
test, the failure occurs at the last `open(); cancel()` after pause/resume; the earlier
STOP, reduced-motion, and focus checks passed. The original failure excerpt is retained
in `docs/evidence/camera-return-deadline-20261111/root-baseline-failures.log`.

## Cause and change

The old test measured `420 + 520 + 64` milliseconds from STOP. These are sequential
animations with scheduled boundaries, not simultaneous transitions:

1. `JarvisWorkWindow.close()` clears `startedAt`; the next shared shell frame establishes
   the actual close start.
2. `finishClose()` sets the window GONE, then posts its cleanup while delivering a frame.
3. `JarvisWorkWindowHost` starts the camera return only in that cleanup's `onClosed` callback.
4. `ContinuumHologramView` advances the camera through its existing budgeted frame callback.

IDLE's existing budget is at least 33 ms, with host vsync scheduling on top. A fixed
64 ms allowance across both boundaries does not guarantee the final camera sample.
No production defect or stalled camera was established. `setWorkActive` already ignores
duplicate targets; `advance` explicitly reaches the exact endpoint at 520 ms.

The two tests now bound window cleanup by the canonical close duration plus first/last
frame allowances from the actual presentation budget. They require both the real window
to disappear and `camera.workActive` to become false. Only then do they advance the
canonical camera duration plus one budgeted frame and host vsync. All exact-zero,
immediate speech cancellation, reduced-motion, focus, pause and non-replay assertions remain.
The helper also rejects a camera reactivated after window cleanup. No production source changed.

## Verification and limits

- 9/9 `AvatarCameraMotionTest` tests PASS with actual unchanged camera/easing sources,
  Kotlin compiler capped at 256 MB and JUnit at 128 MB; no Gradle run by this worker.
- New deterministic case retains the projection until a delayed close, reproduces
  exactly `1.4543533E-5` at elapsed 512 ms, reaches exactly zero at 528 ms and stays zero.
  This is a pure-clock reproducer, not an Android scheduling trace.
- `git diff --check` PASS.
- The first compiler invocation named a nonexistent `Easing.kt`; corrected to the existing
  motion sources. Its input-error log is retained; it was not an application test failure.
- Corrected Android UI tests: NOT_RUN by this worker. Root will run the two failing
  methods first, then the required unfiltered combined suite on the published integration SHA.
- Physical Fold7, GPU, voice/audio and device performance: NOT_RUN. No new visual behavior
  or rendering claim is made for this test-only correction.

Exact commands and source hashes: `docs/evidence/camera-return-deadline-20261111/invocation.json`.
Pure test output: `docs/evidence/camera-return-deadline-20261111/junit.log`.
Rollback: revert this support commit; production artifacts, assets, permissions, releases,
main and signing are unchanged.
