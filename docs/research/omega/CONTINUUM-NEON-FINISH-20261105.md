# Continuum graphics integration — 2026-11-05

Status: WIP / LOCAL_ONLY / NEW_TESTS_NOT_RUN / NOT_RELEASED.

Base: `7ecdafac`, PR #544; graphics draft #546. UI-only integration,
compatible with EXP-05/06/11/12. Original head, orb, C1 compact preferences,
navigation, memory, camera consent and release identity stay intact.

## Checkpoint 1

- `JarvisShellView`: cancellable decorative frame callback; restart after
  attach, focus, visibility, animator-scale and power-save changes. Inactive
  views and reduced motion do not schedule decorative redraws.
- `JarvisOwnerCardContent`: Android device-unlock evidence says
  `Gerät entsperrt`; it does not claim personal identity. Other sensor values
  remain unknown. Regression assertions distinguish recognition sources.
- `git diff --check`: run before commit. Android compilation, tests and device
  lifecycle checks: NOT_RUN here; coordinator owns the combined Gradle run.
- Workspace replacement interrupted the previous local attempt; these edits
  were reconstructed against the restored repository, without claiming the
  previous unsaved state as published.

## Checkpoint 2 — lifecycle review

- Unlock-method constant now belongs to `OwnerRecognition`; the Android source
  retains an alias. Pure card drawing does not depend on the Android source.
- Observer/receiver registrations are independent, tracked and safely released;
  Android 13+ uses the explicit non-exported receiver flag.
- Added `JarvisShellMotionUiTest`: actual attached View drawing after focus,
  visibility, power-save, animator-scale and detach/reattach transitions.
  Tests remain NOT_RUN pending the coordinator's restored Android dependencies.
- `git diff --check`: PASS. No APK/device claim.

## Checkpoint 3 — targeted Opus design integration

Source: PR #535, `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a`.

- Adopted free-head geometry and separate native stage. Dialogue stays left of
  the face; native framing uses the existing renderer's `.70/.92` parameters.
  The head stage follows external rail subtraction and C1 compact geometry.
- Connected existing-clock aura and seven energy curves behind the native
  surface; bottom/shoulder alpha masks apply only to the head child. Foreground
  dialogue stays sharp. No additional avatar animation clock.
- Preserved current original orb, animated tiles, C1 preferences, reattachment,
  asset cache and Activity routing. No asset, TTS, camera or version edits.
- German menu/tile/module/status labels and original #535 bounded text fitting;
  stronger cyan/violet/magenta glass, glow and control surfaces.
- A failed 3D renderer now reports unavailability instead of replacing the
  selected reference head with the unrelated procedural 2D fallback.
- Existing layout/terminology regressions updated against the source reference;
  imported living-scene and German terminology checks plus host preview entry.
- Visually inspected repository references `ref5-fold7-offen.png` and
  `continuum-neural-front-20261104.png`. Neither image was changed.
- `git diff --check`: PASS. Build/tests/new renders: NOT_RUN at this checkpoint.
  `GermanCockpitPreview` is a host composite, not Android Filament evidence.

Next: coordinator runs combined Gradle gates; render production drawing code at
phone, Fold7 inner and landscape sizes; inspect readability and head/orb bounds.

Rollback: revert this graphics branch's individual commits. Do not reset
another branch or delete existing evidence.

## Checkpoint 4 — owner face controls (integration WIP)

- Real production drawing code from integrated root `6c415019` rendered at
  landscape, Fold7 inner and narrow phone sizes. All three images inspected:
  German text, glass surfaces, orb and narrow stacking are readable. The host
  renderer does not include the native GLB head; GPU/head acceptance remains open.
- Added local enrollment, comparison, deletion and immediate stop dialogs with
  a separately confirmed name and camera consent. Opening controls does not
  start capture. Closing or leaving the Activity ends the session.
- Connected the Activity to the agreed real `AndroidOwnerFaceRecognition`
  contract and frame token. Core/controller/camera implementation belongs to
  the avatar branch and is a required integration dependency; no stub is added.
- The existing camera question can use already enabled, idle TTS. Active
  speech/listening and in-flight utterances are not interrupted.
- Added dialog consent/stop/narrow-layout regressions and the enrollment entry
  assertion. These new Android tests are NOT_RUN here; root owns Gradle.
- `OwnerFaceDialogs` compiled with Kotlin 2.4.0 against the avatar branch's real
  `OwnerFacePolicy` and the integrated Android classes. This verifies the dialog
  source, not the new Activity bridge or complete APK. `git diff --check`: PASS.
- No authentication or tool rights derive from face comparison. Actual
  recognition quality, hardware camera behavior and GPU performance remain
  device checks; no successful personal match is claimed.

## Checkpoint 5 — explicit continuation into conversation

- `Zum Gespräch` closes face controls only for the current, explicitly approved
  comparison session. Enrollment cannot continue this way. Ordinary closing,
  STOPPEN and Activity pause still terminate capture and comparison.
- The visible camera stop bar shows the current local comparison result. A
  missing/poor/stale observation replaces a match; stopping clears the label.
- A confirmed current match may greet the confirmed profile name once per
  session through the existing TTS path. Busy/disabled/background speech skips
  that greeting permanently for this session; no delayed replay is queued.
- Six pure `OwnerFaceConversationTest` cases compiled with the real policy and
  passed under JUnit 4.13.2/Kotlin 2.4.0. Dialog source also compiled. New dialog
  continuation/expiry tests await the coordinator's Android run.
- Read the actual avatar-branch controller and Camera2 implementation; the UI
  uses their unchanged token/frame API. Full integrated APK remains a root gate.
