# Code47 host event delivery and APK build

Status at checkpoint: focused regression PASS, original publisher NOT_RUN
on this new source. Physical Fold7/GPU/model/audio/thermal tests NOT_RUN.

## Observed failure

PR587 source 7b9c03804301e00ab9801708eef8a2b50ed3e5f2 failed in original
publisher dep-dbn4sbrfm27c73dbo9n0. Android: 3718 total, 3695 passed,
3 failed, 20 skipped. All three failures waited for the native backend
without dispatching the queued confirmation-button handler.

## Correction

Only OwnerAppActionWorkSceneUiTest changes. Pump the actual paused main
looper until the controlled device port records entry. A durable latch
avoids missing an immediately completed failure. The bounded mock wait
is 60 seconds so host Canvas rendering cannot exhaust its old 10-second
wall-clock budget. Advance the simulated clock for the long running gaze
check; retain frame delivery for the actual animated close/return.

Approval before dispatch, live progress, persistent reading gaze, STOP,
foreground return, missing-app and missing-permission assertions remain.
No production, avatar, original release script or gate changes.

## Fresh verification

- SDK Kotlin test compilation: exit 0.
- Four individually selected cases: 4 passed, 0 failed.
- Whole class, normal JUnit ordering: 4 passed, 0 failed (25.970 seconds).
- Compiled production at 6a6f885f952784c79b209ad39b20d14ccf014df8 has
  byte-identical app/src/main and Gradle configuration to PR587/current.
- Exact commands, source hashes and log hashes are in the receipts. The
  controlled boundary is the app-action port; actual Activity/resources,
  ToolGate, native Canvas and UI callbacks execute on Robolectric.

Source-bound complete publisher checks and actual served-APK verification
remain required. The focused test does not establish device acceptance.
