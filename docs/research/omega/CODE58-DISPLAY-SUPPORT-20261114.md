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
Android SDK or Robolectric dependencies are available. The root integrator will
run the complete class in the existing Code58 exact-source QA workflow.
Fold7 capture/GPU/rotation: NOT_RUN. No installed-device success is claimed.

Compatibility: EXP-12 identity/checkpoint continuity; existing capture-session
and stop boundaries are preserved. No new experiment, renderer or permission.
Rollback: revert this worker patch. Next step: integrate, execute the complete
projection test class, then verify real Fold7 capture and a geometry change.

PORTIERT: worker source ready for integration.
VERIFIZIERT: whitespace only; Android execution pending.
GERÄTEBESTÄTIGT: NOT_RUN.
