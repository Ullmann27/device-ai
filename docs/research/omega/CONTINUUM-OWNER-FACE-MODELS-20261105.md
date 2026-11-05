# Owner face model integration — 2026-11-05

Status: **WIP / AUDIT_IN_PROGRESS / DEVICE_NOT_RUN**.

Source base: `6c4150190b0d3ad5a148a21a6808962b44be2a4d`.
Branch: `codex/continuum-owner-face-models-20261105`.
Scope: model/dependency build integration only; camera, recognition and enrollment UI belong to the other integration tasks.
Relevant experiments: EXP-00 (reproducible Android build), EXP-05 (local inference), EXP-12 (owner identity continuity).

## Fixed inputs

- OpenCV: `org.opencv:opencv:4.13.0`, official Maven Central AAR.
- AAR SHA-256 advertised by Maven Central: `a8c9b0929d8d6367bb476320a1f36587db2b57ea18d8f7521ce509514903ca04`; binary inspection pending at this checkpoint.
- OpenCV Zoo commit: `48b10fa505aeb51fbd127fc60a5c660040e823b7`.
- YuNet: `face_detection_yunet_2023mar.onnx`, 232589 bytes, SHA-256 `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4`.
- SFace: `face_recognition_sface_2021dec.onnx`, 38696353 bytes, SHA-256 `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79`.
- Runtime asset names agreed with the camera/recognition implementation: `faceid/` plus the original model filename.

## Required behavior

Generated Gradle build assets contain verified model bytes. Model weights never enter Git. Text licenses and a provenance manifest are tracked. Cached bytes are checked before use; size/hash/path failures block packaging. The existing original-package signing and publication commands remain intact.

## Validation boundary

The baseline full Node run on the source base executed 2393 tests: 2360 PASS, 2 FAIL, 31 SKIP. Both failures are obsolete portrait-reference source assertions and are being corrected by the integration owner. Its source remained unchanged. This is not a model, APK, native ABI or device result.

Pending: original model/license downloads, AAR size/ELF inspection, fail-closed downloader checks, central Gradle integration, final APK asset/hash and ZIP alignment checks. Real owner recognition, spoof resistance and Fold7 behavior are not verified by these host checks.

Rollback: revert only this branch's Gradle/model-build integration before final source freeze; no owner enrollment data or original signing material is changed by this task.
