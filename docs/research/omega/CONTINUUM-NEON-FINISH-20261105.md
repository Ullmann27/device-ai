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
