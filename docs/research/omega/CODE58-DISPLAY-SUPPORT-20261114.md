# Code58 display support

Integration target: `fix/code57-fold7-downloads-research-20261114`, PR #836.
Support integrator: `work/code58-three-agent-support-20261114`.
Worker: `work/code58-support-display-20261114`.
Audited base: `1656443d137bb6787c2eabe69a3e91ff1b2275b2`.

## Defect and correction

`ContinuumLiveProjectionService.ticket()` replaces the capture surface after a
window-geometry change. `updateSurface()` first installs a replacement reader,
then detaches and reattaches the VirtualDisplay surface. If either surface call
throws, its finally block retires the old reader. The enclosing `onImage()`
previously swallowed the exception and merely paused the monitor. Capture could
remain active with no working producer surface and no later image to retry.

The geometry-reset path now calls the existing `failCapture()` on this error,
matching the resize/configuration callback behavior. The active session ends
with `SOURCE_UNVERIFIED`; the latest frame, display, reader, foreground service
and projection are released by the existing cleanup. No automatic restart or
consent reuse is added. A successful reset and all source/crop/revision checks
are unchanged.

This is a statically identified additional failure path. It is not a claim that
this exception caused the owner's current black screen. Code56's existing
own-content invalidation-loop repair remains intact.

## Verification

Three new cases in `at.heinz.deviceai.ContinuumLiveProjectionServiceUiTest` use
the production `ticket()` and `updateSurface()` calls with controlled Android
window and VirtualDisplay boundaries:

- `geometryResetDetachFailureEndsCapture`: detach throws; capture ends, the
  retained frame is released, reader/display are cleared, late visibility and
  resize callbacks cannot revive it.
- `geometryResetAttachFailureEndsCapture`: reattachment throws after detach;
  the same cleanup and late-callback assertions hold.
- `successfulGeometryResetKeepsCaptureAndRejectsOldReader`: the new reader
  obtains a ticket; the retired reader cannot obtain one; capture remains active.

Existing projection tests are retained. The added fixture reuses the existing
split-window accessibility shadows; no GPU success or actual pixels are mocked
into an owner-facing result.

Local `git diff --check`: PASS. Android/JUnit execution: NOT_RUN locally; no
Android SDK or Robolectric dependencies are available.

First actual GitHub run `43566195472`, job `132360472369`, integrated source
`8982225c0b32429a5c8ebd4332dbd0b9061b2676`: projection class 21 cases,
18 passed, 3 failed, zero skips. The three new cases failed during fixture
creation with `IllegalArgumentException: The image dimensions must be positive`;
the service-only Robolectric context returned 0x0 maximum window bounds.
The tests had not reached the production reset path.

The fixture now supplies explicit 1200x1000 `WindowMetrics` at the
`WindowManagerImpl.getMaximumWindowMetrics()` Android boundary. Both the fixture
and production `displayBounds()` read this same geometry; an assertion checks
their agreement. Both accessibility application windows occupy disjoint
600x1000 halves. The existing source/crop/cleanup assertions are unchanged.
This uses the same custom WindowManager shadow approach as AndroidX Camera's
`TestShadowWindowManager`; no production display geometry is changed.
Corrected-fixture Android run `43566865117`, job `132362508543`, source
`c6ca29c92f34a6204d9151f08ab8e4f783663558`: 181/181 selected Android/Robolectric
cases PASS, zero failures and skips. The required-case gate explicitly checks
all three new geometry-reset tests. This verifies the capture failure lifecycle
in the real service methods under controlled Android boundaries.
Fold7 capture/GPU/rotation: NOT_RUN. No installed-device success is claimed.

Compatibility: EXP-12 identity/checkpoint continuity; existing capture-session
and stop boundaries are preserved. No new experiment, renderer or permission.
Rollback: revert this worker patch. Next step: integrate, execute the complete
projection test class, then verify real Fold7 capture and a geometry change.

PORTIERT: worker source ready for integration.
VERIFIZIERT: whitespace PASS; corrected Android/Robolectric execution PASS at
the exact source above. PR #839 carries the final combined-source receipt.
GERÄTEBESTÄTIGT: NOT_RUN.
