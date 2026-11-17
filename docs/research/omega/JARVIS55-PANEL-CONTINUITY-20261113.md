# Jarvis55 panel continuity

Status: WIP / UNVERIFIED. Source base: `84268a0b868468074bf9ac904e3682ed885bf058`.
Owner: cognition development lane. Integrator: root; target `integrate/code55-apk-fable-20261112`.

## Defect and connection

Rebuilding Home invoked OwnerComputerSessionController.restoreFeed but reattached only the live feed. Its new dashboard lost the accepted task/result panels until another work event arrived. The viewport path separately copied an older complete dashboard after restore, which could restore stale panels even after capture revocation.

The controller now retains only its latest accepted ComputerPanelState and re-emits it on Home restoration. A restore or queued snapshot requires current Plan identity, task, target package, original capture session, a bound non-ended feed and AndroidLiveComputer.checkActive. At these publication/restoration boundaries, revocation, an ended source or a changed binding clears the retained presentation. Stop clears it before any successor task, and final result publication uses the same source validation. Viewport reflow restores telemetry while keeping the newly validated computer field.

The existing CockpitWorkReducer remains the only event reducer. The retained value is presentation data, not authority for actions. No model/permission/Android dispatcher changes. Temporarily stale frames do not erase the task history or falsely mark video live; the existing live-monitor status remains authoritative.

## Checks and limits

Source review: read the actual controller, Home, Activity rebuild, AndroidLiveComputer and frame-binding paths.
New unit cases cover old/new task, capture-session and package mismatches, missing/ended/revoked source, and pending/paused stages.
Tests, Android compilation, lint, native/device execution: NOT_RUN after runtime became unavailable. Existing earlier 12/12 side-panel checks do not validate this follow-up.

Next: integrate this exact diff through root, run ComputerPanelPresentationTest and full source QA. Exercise Home recreation during an active and completed task, then revoke capture and rebuild; old panels must not return. No new Android action may execute due to restoration.
Rollback: revert this commit's controller, reflow, presentation validation and tests.
