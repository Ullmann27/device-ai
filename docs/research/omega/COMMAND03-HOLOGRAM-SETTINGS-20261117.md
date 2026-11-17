# Command03 hologram and settings

Target: `integrate/continuum-command03-android-20261115` / PR #851.
Owner: PR #859. Base: published Code61, `5e995146354cc4f338fbff564b39b349b7aa74f5`.

## Source-bound status

- PORTIERT: PR #859 merged as `00b3c8b8412da4b4a6a98f8236dcfd11543c1f5a`.
- VERIFIZIERT: CI 44035212452 at `3fe1ae65dab84887d65ae9bf7e0d8120d84d31af`;
  49/49 DOM, 19/19 Chromium layout and 14/14 focused Android tests PASS.
  The merge tree exactly equals the tested tree `2c1f64ff1be450d68541c250e4a966f1fb9a37dd`.
- GERÄTEBESTÄTIGT: OPEN; no physical Fold7 or device/GPU acceptance claimed.

## Delivered

Cyan/violet neon glass and restrained hologram light motion. Persistent light
strength and motion controls, reduced-motion support, background pause and
non-intercepting light layers. All 35 original assets and the Code61 responsive
repair remain intact. Visible labelled settings entry, four direct shortcuts
(voice/model/internet/hologram), and a searchable 21-entry native settings hub.
Existing native handlers remain authoritative; previously hidden choice lists
in personalisation, Cinema, training and autonomy are visible again.

Screenshots at 320, 412 and 834 px and multiline bottom layout reviewed.
Small-screen shortcut labels increased to 11 px. Native tests exercise actual
Activity routes/dialogs, hub search/accessibility and persisted appearance.
No full application regression or model/experiment-interface change claimed.

## Code62 build and published release

Run: https://github.com/Ullmann27/device-ai/actions/runs/44035893856
Build: SUCCESS. Source: `00b3c8b8412da4b4a6a98f8236dcfd11543c1f5a`.
Package: `at.heinz.deviceai.continuumlocal`; versionCode 62 / versionName 0.3.61;
ARM64. Artifact 14307113627, archive SHA256
`658ea48ef891977260177252a80b491a859a52e7ec5cce36e051b0f0bf65cbf2`.
Downloaded artifact source, version/ABI record and all 38 packaged Command03
files checked against the integrated source. Unsigned APK: 111460770 bytes;
SHA256 `5456f14091e6b2e02021bc2f94d28b4319b94486594c5fee21490e56b274dd6c`.

Owner approval completed. Build, original-signature/identity verification and
publication in run 44035893856 are SUCCESS. Published 2026-11-17T07:18:39Z.
Release: https://github.com/Ullmann27/device-ai/releases/tag/continuum-einfach-code62-44035893856
APK: https://github.com/Ullmann27/device-ai/releases/download/continuum-einfach-code62-44035893856/Continuum-Code62.apk
Signed APK: 111480481 bytes; release asset SHA256
`c4e22b5a0443b1d7a4f1f9e922abae5a66a445d61255cc32008b113ad83a3def`.
The signing job verified the existing certificate and APK v2 signature and
published the source-bound build. Signed backup artifact: 14307643081.
Next: owner tests phone/Fold layout and settings. No replacement pipeline,
main merge, automatic installation or certificate change.

Rollback: revert PR #859; no stored-content migration.
