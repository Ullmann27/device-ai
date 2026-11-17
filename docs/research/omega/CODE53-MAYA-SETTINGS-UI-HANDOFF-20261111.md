# Code 53 Maya settings lifecycle regression

- Integration owner: root; canonical branch `integration/continuum-final-visibility-20261111`.
- Source baseline: `fd86f11a4a822ee4805f4c462791baeb1d741db4`.
- Failure evidence: GitHub run `42977884118`, job `130381258271`; 4,668 Android tests, one failure, 17 skipped. `MayaVoiceSettingsUiTest.kt:69` failed at `assertNotNull(input.error)`.

## Cause and patch

`showMayaVoiceSettings` installs the positive-button validation from `onShow` and clears entered credentials from `onDismiss`. Android API 28 dispatches both listeners through queued messages. Robolectric 4.14.1 defaults to a paused main looper. The test clicked the positive button before delivering `onShow`; its later wipe assertion also preceded delivery of `onDismiss`.

The test now explicitly selects `PAUSED`, drains the main looper after opening dialogs, after the activation click, and after dismissal. Existing validation, secure-window, autofill, no-speech, and credential-wipe assertions remain. It additionally verifies that an invalid key neither enables Maya nor stores credentials after the click. Production code is unchanged.

Primary source evidence:

- [Android API 28 Dialog.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/core/java/android/app/Dialog.java): `show` / `dismissDialog` enqueue listener messages through `sendShowMessage` / `sendDismissMessage`.
- [Robolectric 4.14.1 LooperModeConfigurer](https://github.com/robolectric/robolectric/blob/robolectric-4.14.1/robolectric/src/main/java/org/robolectric/plugins/LooperModeConfigurer.java): default `Mode.PAUSED`.

## Verification and handoff

- Source review: lifecycle sequence and preserved assertions checked; `git diff --check` passes.
- Local Android/Robolectric execution: **NOT_RUN**. The local cache lacks Robolectric and its instrumented Android runtime; only a compile-time Android stub jar is available. No new SDK installation or parallel CI was started.
- Next gate: run `at.heinz.deviceai.MayaVoiceSettingsUiTest` in the existing hosted QA preflight, then retain the full Android suite and every mandatory class.
- No change to signing, package identity, voice policy, credentials, network consent, or production dialog behavior. No device or listening acceptance claimed.
- Rollback: revert this test-only lifecycle adjustment if hosted evidence identifies another cause; do not remove assertions or bypass the QA gate.
