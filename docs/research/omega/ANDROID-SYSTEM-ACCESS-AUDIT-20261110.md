# Android system access audit

Final integration note: incorporated in PR #735; complete Android source compilation
and 155 selected integrated tests pass on published `02120f079ce1bdb1d3311f16f88d1a51bb7fbe09`.
The scoped results below are historical checkpoints; final scope/provenance is in
[the integration handoff](ANDROID-SYSTEM-INTELLIGENCE-HANDOFF-20261110.md).
Physical device validation remains NOT_RUN.

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

## Verification

- Pure Kotlin/JVM `ShizukuCapabilitiesTest`: 8/8 PASS.
- Actual bridge/Robolectric API28 lifecycle regressions: 21/21 PASS, including seven new controls.
- Original bridge: five revocation regressions FAIL; corrected exact sources: the same five PASS (subset of 21).
- Full Android integration compile: pending root.
- Fold7, real Shizuku/ADB, root device and UI input: NOT_RUN.

Rollback: revert this patch; no persisted permission state or schema changes.
Source-bound logs and replay: `docs/evidence/android-system-access-20261110/`.
Next: root integrates into #735 and runs full Android checks; retain device and Play gates.
PORTIERT: ready for root integration; VERIFIZIERT: scoped host; GERÄTEBESTÄTIGT: no.

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

## Actual paths at the audited base

| Capability | Normal Android A | Shizuku/ADB B | Optional root C | Existing path and limit |
| --- | --- | --- | --- | --- |
| Volume totals/free space | Present, own volume API | Not needed | Not needed | `DeviceToolExecutor.get_storage_info → DeviceStorageProbe` |
| Battery level/charge/temperature | Present, sticky Android broadcast | Not needed | Not needed | `DeviceBatteryProbe`; battery temperature is not CPU temperature |
| Installed launch-app lookup | Present within package visibility | Broader package-list command exists | No extra need | `find_app → AndroidInstalledAppLookup`; `list_installed_apps` still shell at base |
| Own/other app storage bytes | Own statistics API available; foreign package needs usage access | No reliable aggregate adapter at base | Disabled | Do not infer bytes from package names or file-list output |
| Package/process diagnostics | Native information can be added through existing executor | `get_app_info`, `list_processes`, `read_logcat` exist | Disabled | Fixed shell templates; process output and logs may contain private data and are not used by the new identity probe |
| Shared-file search/list/size | Selected-document adapter needed | Fixed shared-path tools present | Disabled | A hardcoded `/sdcard` root allowlist is not a normal Android user grant |
| Launch app + observed result | Connected after exact package approval | Not needed | Not needed | `AndroidDeviceAppActions → VerifiedForegroundLaunch → ScreenObservationBus`; success requires new target window event |
| Read screen once | Connected after approval and accessibility enablement | Not needed | Not needed | `read_app_screen → BoundedScreenReader`; editable/password nodes redacted, no event text persistence |
| Verified element click | Native candidate exists, not connected | Not needed | Disabled | `ScreenObservationBus.clickCandidate → VerifiedNodeClick → AndroidNativeClick`; no registered tool or production approval path |
| Type, coordinate tap, back/home | Interface/route candidates only | Fixed `input` builders only | Disabled | `chief/DeviceControl.kt`; no production `DeviceControlRouter` construction or concrete `AccessibilityControlPort` implementation found |
| Arbitrary shell | Not in ordinary app APIs | Legacy manual expert screen exists | Now blocked | `MainActivity → CommandInterpreter → ShizukuBridge.execute`; not exposed as an LLM tool; excluded from system diagnosis |
| Current identity | Not needed | New fixed probe validates server/service UID and SDK | Detection only | Root and unknown UID cannot run normal bridge commands |

The screen service reads event metadata to invalidate stale observations. It does not
capture screen text on events. One-shot reading requires a target request; launching
uses a normal intent followed by a fresh observed window. None of this proves real
Fold7 behavior in this session.

## Prioritized integration

1. P0: ship the revocation/identity fixes through #735 and keep tool results local.
2. P1: use normal Android APIs for basic device/battery/volume/process-self/thermal
   data. Unknown values remain unknown; free RAM alone is not a fault.
3. P1: use the system picker for selected files or supported subtrees. Android 11+
   disallows granting the entire Download root via `ACTION_OPEN_DOCUMENT_TREE`, and
   forbids Android/data and Android/obb access through these SAF routes. Selected
   downloads can be requested through `ACTION_OPEN_DOCUMENT`; never bypass denial.
4. P1: optional Usage Access through Android Settings for permitted foreign-app
   storage statistics. Handle `SecurityException`, unavailable volumes and partial
   package visibility; no blanket promise of all apps or per-app energy attribution.
5. P2: offer exact read-only Shizuku diagnostic actions individually, with bounded
   output/deadline and current capability checks. `dumpsys` executable presence does
   not establish that a service dump is allowed or parseable on a vendor build.
6. P2: for UI navigation, reuse the existing verified-node candidate only after
   connecting a narrow human-defined action, exact preview/package/window binding,
   per-action approval, cancellation and fresh postcondition. Do not automatically
   activate the existing generic coordinate/text router from model plans. Messages,
   checkout/payment, account/security changes remain outside this prototype.
7. P3: root expert actions remain disabled/unimplemented. Future support needs its
   own explicit user activation through the existing approval authority and exact
   resource/risk/rollback/backup checks; no system-partition, SELinux or thermal writes.

## Play and platform boundaries

A general AI assistant is not automatically an eligible accessibility tool. The
current Play policy permits narrow deterministic human-defined automation but
prohibits autonomous Accessibility initiation, planning and execution for ordinary
assistants. Therefore an unrestricted AI screen-control loop cannot be presented as
Play-compliant. Android API availability and Google Play distribution approval are
separate gates. No `isAccessibilityTool=true` claim is added.

The existing manifest requests `QUERY_ALL_PACKAGES`; this audit neither grants nor
establishes Play eligibility. Prefer scoped `<queries>` for concrete workflows when
sufficient. Any required broader visibility needs justified declaration/review.
The existing accessibility XML requests read-window content but not gesture injection.
No declaration or entitlement is silently widened by this patch.

## Additional primary sources

- [SAF access and restrictions](https://developer.android.com/training/data-storage/shared/documents-files)
- [StorageStatsManager](https://developer.android.com/reference/android/app/usage/StorageStatsManager): own package needs no usage grant; foreign-package statistics require user-granted usage access, and calls belong off the UI thread.
- [Package visibility](https://developer.android.com/training/package-visibility): visibility filtering and Play review of `QUERY_ALL_PACKAGES`.
- [PowerManager](https://developer.android.com/reference/android/os/PowerManager): thermal status is distinct from temperature; headroom can be unavailable/NaN and frequent polling is unsuitable.
- [Android accessibility actions](https://developer.android.com/guide/topics/ui/accessibility/views/service): official UI actions exist but do not replace consent or Play restrictions.

All source URLs checked 2026-11-10. Policy review is not a Play approval, Android
hardware result, security certification or proof of currently installed app behavior.
