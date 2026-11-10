# Android system access audit — checkpoint

Base: Cinema `c1fdbaeab95d8f808715c180fbc77762e459a59c`.
Integration: root / `feature/android-system-intelligence-20261110`, PR #735.
Experiments: EXP-08/EXP-11; existing ToolGate, Shizuku transport and screen service only.

## Implemented bridge correction

`isReady()` now checks current Shizuku permission; checked calls recheck permission,
service generation and UID before dispatch and after return. Revocation no longer
leaves the bridge apparently ready. Existing bind watchdog, permission watchdog,
single-flight dispatcher and old-callback rejection remain.

`probeCapabilities(callback: (Result<ShizukuCapabilities>) -> Unit)` runs one fixed,
bounded, read-only probe through the existing checked dispatcher. It verifies actual
user-service UID against Shizuku server UID and actual SDK against the Android app SDK.
The three executable-presence flags are observations, **not** permission claims.
Every actual diagnostic still needs its own successful checked result.

Regular bridge commands require UID 2000. UID 0 is reported as root with expert mode
disabled; only the exact identity probe may run. No root action opt-in is implemented,
no root writes or security changes are allowed. Unknown identity fails closed.

## Verification at checkpoint

- Pure Kotlin/JVM `ShizukuCapabilitiesTest`: 8/8 PASS.
- Added actual bridge/Robolectric lifecycle regressions: NOT_RUN at this checkpoint.
- Full Android integration compile: pending root.
- Fold7, real Shizuku/ADB, root device and UI input: NOT_RUN.

Rollback: revert this patch; no persisted permission state or schema changes.
Next: run lifecycle regressions, finish current-source Android/Play access matrix.

## Current official guidance reviewed

- [Shizuku API](https://github.com/RikkaApps/Shizuku-API/blob/master/README.md): UID 0
  is root, UID 2000 is ADB; available permissions vary by Android version. User service
  identity differs from normal app identity; command availability is not permission.
- [Google Play AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en):
  a general assistant cannot claim accessibility-tool status merely because it helps
  some disabled users. Autonomous Accessibility initiation/planning/execution is
  prohibited for non-verified accessibility tools. Narrow deterministic human-defined
  automation remains possible with required disclosure, consent and declaration.

Sources retrieved 2026-11-10. Policy compliance is not established by host tests.
