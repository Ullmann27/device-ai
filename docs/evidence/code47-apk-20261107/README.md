# Code47 host event delivery and APK build

Final status: RELEASED / SERVED_APK_VERIFIED, 2026-11-07 03:27 UTC.
Original publisher full QA PASS; original-signed Code47 is live.
Physical Fold7/GPU/model/audio/thermal tests NOT_RUN.

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
subsequently passed as recorded below. Host QA does not establish device acceptance.

## Original publisher and binary readback

- Frozen APK source: `7732b8668f912419d7aac7a1609a97097d727cbf`.
- Original service: `srv-dbf1tr2vcj2c73ehpfog`.
- Deployment: `dep-dbn8vhpsrm7s738np9jg`, LIVE at 03:26:55 UTC.
- Full mandatory Node/backend/Android/UI/lint and unsigned assembly gates
  passed before original signing. `publisher-pass.json` preserves the
  source-bound presign PASS, required-suite marker, release lint/build
  success, catalog identity, signed-staging and live markers.
- Android aggregate test totals are not exposed in these success logs;
  the historical 3718-test totals above are from the earlier failed source.
- Independently downloaded the immutable served APK, then verified its
  SHA-256 and byte length against the strict catalog, package/version
  using aapt2, original certificate using apksigner, ARM64-only libraries,
  original avatar GLB bytes, and source/hash in served BUILD-INFO.
- This documentation follow-up does not alter the frozen APK source or
  retrigger the publisher. Main remains unmerged; no device installation.

Download: [0.3.46 / Code47](https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-35f93b41ed835bf2ec16890b62d4e640d3b1ea28c2e8a098065bd08f64dc2481.apk).

Package: `at.heinz.deviceai.continuumlocal`. Size: `101925655` bytes.

APK SHA-256: `35f93b41ed835bf2ec16890b62d4e640d3b1ea28c2e8a098065bd08f64dc2481`.

Certificate SHA-256: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.

Raw binary-readback receipts: `served-apk/`. Checksums cover the preserved
receipts, not a claim of physical-device acceptance.
