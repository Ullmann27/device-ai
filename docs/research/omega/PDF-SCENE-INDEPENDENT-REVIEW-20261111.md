# PDF scene independent review

Status: WIP / NOT_RUN. No product change in this checkpoint.

Canonical integration owner: root, branch `work/pdf-scene-20261111`.
Review baseline: `4e4840a01b7954c416722b048a6e4f1ec85b5a43`.
This includes #747 `7b1d0f67b4838075303cb2b8fc2c99192d1c7cc3`
and #757 `0adcfe907baaaab9f53730fcbab67ef61418ebce`.

## Existing proof and gaps

- `ContinuumDocumentSceneUiTest` executes the real picker entry, but constructs
  the `Document` and page bitmap. It does not execute `PdfDocumentIntake.read`.
- `ContinuumDocumentFlowUiTest` executes the application draft path using a
  controlled local model. Its result-origin expectation is the document center,
  which predates the owner's result-from-right requirement.
- Existing flow cases cover STOP, late model responses, failure, missing
  remembered PDFs, work-journal state and document-reading gaze.
- Native Canvas evidence is host evidence. It does not establish Filament,
  Android PDF native decoding, actual model quality, audible speech or Fold7.

## Bounded acceptance work

Review authentic PDF selection and denied/stale callbacks, directional entry,
result-to-speech ordering and cancellation against the actual production path.
Use the existing UI test source set and capture clock. Coordinate changes with
the flow/graphics owners, then execute once on the combined committed source.

No APK, signature, release, main merge or model-weight change is authorized by
this support work. Keep the original avatar and orb. Findings remain explicit
until reproduced; a historical green run is not a current-source pass.

## Local environment

Read-only inspection found Gradle 8.11.1 and Android API36 classes under
`/tmp/continuum-check`. The default Gradle home has no usable Android dependency
cache. No Gradle build, renderer or physical device test was started here.

Relevant existing runner: `tools/capture/capture_jarvis.py`, paired with
`tools/capture/force_capture.gradle`. Root owns the shared build resource lease.

## Rollback

This checkpoint is documentation only. Revert its commit to remove this review
record; it changes no runtime behavior.
