# PDF scene independent review

Status: WIP. Review tests authored; application compilation and execution
pending on the combined source. This support branch changes tests and review
documentation only.

Canonical integration owner: root, branch `work/pdf-scene-20261111`.
Review baseline: `4e4840a01b7954c416722b048a6e4f1ec85b5a43`.
This includes #747 `7b1d0f67b4838075303cb2b8fc2c99192d1c7cc3`
and #757 `0adcfe907baaaab9f53730fcbab67ef61418ebce`.

## Existing proof and gaps

- `ContinuumDocumentSceneUiTest` executes the real picker entry, but constructs
  the `Document` and page bitmap. It does not execute `PdfDocumentIntake.read`.
- `ContinuumDocumentFlowUiTest` executes the application draft path using a
  controlled local model. The review changes its old document-center origin
  expectation to the owner's result-from-right requirement.
- Existing flow cases cover STOP, late model responses, failure, missing
  remembered PDFs, work-journal state and document-reading gaze.
- Native Canvas evidence is host evidence. It does not establish Filament,
  Android PDF native decoding, actual model quality, audible speech or Fold7.

## Bounded acceptance work

Added behavioral checks use the production Activity, actual Android picker
intent and captured request code, real denied resolver access, real frame
pumping, and the existing controlled local model/Android speech shadow:

- the owner's exact PDF visibility request, cancelled selection and repeated result;
- STOP, a new owner turn and an old picker result arriving after a newer picker;
- paused result delivery, foreground read failure, and Activity recreation;
- a silent draft until the right-hand result window arrives, one personal
  follow-up, and suppression after STOP or backgrounding;
- cooperative read cancellation before provider access.

`PdfIntakeNativeDecodeProbeUiTest` is a separate capability probe within the
focused scope: actual synthetic PDF bytes, a provider returning their file
descriptor and metadata, and production `PdfDocumentIntake.read` on API35.
It requires native render output and the PDF's own text. Its first invocation
is strict. Missing host PDF JNI must be recorded as such, never as a PDF pass.
The Canvas scene tests still use an injected page/document and controlled
model; they do not acquire native decode or model-quality evidence by proximity.

The reproducible 24-class scope is `tools/pdf-scene-review/focused.init.gradle`.
It also includes the existing window lifecycle, STOP, gaze, depth, geometry,
document-intent and prompt regressions. It changes no production source or
QA task guard. No additional optional test suites are planned.

No APK, signature, release, main merge or model-weight change is authorized by
this support work. Keep the original avatar and orb. Findings remain explicit
until reproduced; a historical green run is not a current-source pass.

## Local environment

The full toolchain was restored without changing project dependencies:

- Gradle 8.11.1; existing cached AGP8.10.0/Kotlin2.4.0.
- Full Temurin JDK21.0.13+9, SHA256
  `d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c`,
  checked against the official Adoptium release metadata. `javac -version`
  actually returned 21.0.13. The original system installation lacked JDK launchers.
- Full Android API36 revision2 and build-tools35.0.0. The build-tools archive
  matched Google's repository SHA1 `2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc`.
- The project's existing face-model preparation completed and verified its
  pinned OpenCV, YuNet and SFace hashes. No model pin was changed.
- Inherited network proxy and system Java CA trust were used; TLS validation
  remained enabled.

Environment-only Gradle results, with no application tests executed:

| Task | Observed result |
| --- | --- |
| Offline `help` | PASS, 2m12s |
| Main/runtime/SDK dependency preparation | PASS, 3m26s |
| Initial raw unit-test configuration resolution | FAIL, AGP self-project artifact ambiguity |
| Corrected external-module unit-test artifact resolution | PASS, 1m10s; 53 compile and 59 runtime artifacts |

The failed preparation is retained; it was not an application compile or
test failure. The corrected preparer selects external modules and does not
replace normal AGP artifact selection. The preparation lease has ended;
the coordinator owns the actual application compile and test run.

Relevant existing runner: `tools/capture/capture_jarvis.py`, paired with
`tools/capture/force_capture.gradle`. Root owns the shared build resource lease.

## Source review boundary

Frozen production checkpoint `09757b938734d7bf12347c3e5f69115f11c5621b`
has tree `4ff6d6a764fcca3fa1f07ba1af6a3f6021bfc3da`. Relative to the review
baseline, build/dependency configuration and existing avatar assets are
unchanged; the sole asset delta is new `cinema3d/document-workspace.glb`.
This verifies source identity, not Filament output or Fold7 performance.

Robolectric4.14.1's downloaded shadows-framework and nativeruntime archives
contain no PDF renderer shadows. The actual instrumented Android35
`PdfDocumentProxy` references the `pdfclient` library. This is static capability
inspection only; the strict dynamic decode probe remains necessary.

PR761 review comment4537778447 was confirmed in source: the existing
"no dim before glass" assertion supplied openness0, so the early openness
guard masked the glass factor. The test-only correction supplies openness1.
At `BRACKETS_END_MS=320`, glass remains0 because `GLASS_START_MS=480`.
The focused suite already includes this same test; no new scope was added.

## Rollback

Revert the support commits to remove their review tests, filter and record.
They change no runtime behavior. No APK, signature or installed build is
produced by this branch's completed work.
