# Code55 Android source and action verification

Base: `8a882e662cb48e3363213eb5005cb091828d89ec` (#811), descendant of
`779863955825b65931ef569dde79a6d2f8f8c771` (#809). Integration target: #812,
then the existing Code55 integration branch. EXP-07/08/11 contracts retained.

## Repair

The submitted-frame check in `AndroidLiveNodePort` validated task, capture,
window, freshness and crop, but did not compare `observationRevision` with
the target observation. A still-fresh image from before a content change
could therefore authorize an input against newer accessibility contents.

The existing `LiveMonitorSubmission` now checks that exact revision, with all
prior source/crop checks preserved. Older fresh images remain displayable;
they cannot authorize newer or older targets. MediaProjection rejects a
copy spanning an observation change. The port rechecks cancellation and
revision after permission/render checks and before native input. No retry,
new action authority, renderer, event bus or model changes.

## Reproduction

```
python tools/code55-apk/render-action-check.py --deps /path/to/kotlin-jars --out /tmp/render-action-green
python tools/code55-apk/render-action-check.py --deps /path/to/kotlin-jars --out /tmp/render-action-mutation --omit-revision-check
```

The harness compiles actual production classes with Kotlin 2.0.20/JDK,
extracting only the original Risk and ToolExecutionStatus declarations.
It records source hashes. It does not simulate Android or a GPU.
41 host JUnit tests pass, including seven new regression tests and 34
existing frame, submission and action tests. The mutation deliberately
removed only the new revision comparison: 4 of the 41 tests fail as expected.
Android compile, rendering, real input, thermal and Fold7 proof remain NOT_RUN.

## Full QA diagnosis at base #809

Read actual job 131480796016 from run 43297198210 and artifact 13985320264.
Source: `779863955825b65931ef569dde79a6d2f8f8c771`.
5286 tests executed: 34 failures, 18 skips. Configured lint passed.

- 27 cockpit/presenter/layout/UI failures: includes legacy work-window and
  button contact assertions, protected face bounds, retained orb size, and
  an app-chooser test expecting an AlertDialog ListView.
- Five research approval speech tests expect microphone readiness before
  the Android ready callback. Cognitive lane is investigating event ordering.
- One hands-free silence retry assertion differs from the current policy.
- Native PDF: `NoSuchMethodError: java.io.FileDescriptor.getOwnerId$()` from
  `libcore.io.IoUtils.acquireRawFd` through `PdfProcessor.create` before any
  PDF content assertion. This is a host Android/JVM compatibility failure.

The existing strict PDF probe correctly fails. Do not enable its opt-in
assumption or replace it with synthetic text to claim full QA. Robolectric
4.17's official tree has no PDF renderer/processor shadow; changing JDK
alone is not an established repair for Android-specific FileDescriptor
methods. A real supported native runtime or Android instrumentation is
required to prove provider → PDF render → text extraction.

Related official sources inspected:
https://github.com/robolectric/robolectric/tree/robolectric-4.17
https://github.com/robolectric/robolectric/blob/robolectric-4.17/shadows/framework/src/main/java/org/robolectric/shadows/ShadowParcelFileDescriptor.java

## Build route and remaining verification

Run 43297197973 has successful `scoped-android` and `unsigned-arm64` jobs,
but proves only its original source. Existing workflows are explicitly
bound to `integrate/code55-apk-fable-20261112`; a dispatch on a different
branch skips the jobs. Root integrator coordinates a fresh exact-source
build after integration. Do not weaken branch/source checks silently.

Initial local inspection found only JDK, Kotlin/JUnit, an API jar and partial
Gradle caches. This agent subsequently installed user-owned Gradle 8.11.1
and Android cmdline-tools 19, platform36 revision2, build-tools35 and
platform-tools37.0.1. Gradle matches the wrapper SHA256; command-line tools
match the official repository XML checksum. Gradle, sdkmanager, aapt2 and
adb version probes passed. No APK was built by this agent. No signing files
were read. Root integrator coordinates the combined build.

Rollback: revert this repair commit. Device risk: rapidly changing content
can now pause actions until the rendered pixels and target revision agree;
this is deliberate fail-closed behavior requiring device usability checks.

## Real native PDF probe (additional test route)

`ContinuumVisualInstrumentation` accepts `-e probe pdf`. A test-APK-only
provider serves one generated synthetic PDF, rejects every other path and
all writes, and exposes no owner file. The production `PdfDocumentIntake`
reads its actual ContentResolver descriptor, renders pixels, extracts text,
and verifies page attribution. There are no PDF mocks or skipped assertions.

Build on the disposable emulator lane (existing mentor variant, not an owner
update), with signing environment variables absent:

```
gradle -PdeviceAiMentorPreview=true :app:assembleDebug :app:assembleDebugAndroidTest
bash tools/code55-apk/native-pdf-emulator.sh emulator-5554 /tmp/native-pdf-evidence
```

The script refuses physical devices, API below 35, wrong packages and any
instrumentation result lacking success plus actual nonzero rendered ink.
Use a clean API35/36 x86_64 emulator. This adds a real test route; it does not
relax or conceal the current strict host-PDF failure. Emulator execution is
NOT_RUN until an exact-source receipt exists. The probe and production PDF
reader compile together against the Android API jar; full instrumentation
packaging remains to be run by the integrator.

Official Android15 `PdfDocumentProxy` confirms `createFromFd`, rendering,
page text and cleanup call native `pdfclient`. A host FileDescriptor shadow
alone does not provide that native PDF runtime:
https://android.googlesource.com/platform/packages/providers/MediaProvider/+/android15-release/pdf/framework/java/android/graphics/pdf/PdfDocumentProxy.java
