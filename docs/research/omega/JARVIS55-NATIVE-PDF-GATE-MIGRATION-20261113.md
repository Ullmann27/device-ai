# Native PDF gate migration

Base: `41306257ce03c1e22e998877bf769ce42b3a0486`, tree `3a16a64f4d831ba51d54f24cbb952d850e8d433e`.

Native proof: [run 43344572298](https://github.com/Ullmann27/device-ai/actions/runs/43344572298), job `131620674180`. The actual API35 Android instrumentation passed: one provider query, one file open, one page, 506 ink pixels, status `ok`, instrumentation exit `-1`. Exact output and source blobs are retained in `docs/evidence/jarvis55-native-pdf-20261113/`.

## Scope

Retire only `PdfIntakeNativeDecodeProbeUiTest`, the single duplicate native-decode success case from the Robolectric host suite. Its Android PDF processor calls `java.io.FileDescriptor.getOwnerId$()`, which the host JVM does not implement. The same production `PdfDocumentIntake` now executes through the real Android implementation in the already mandatory `native-pdf` job.

This migration follows an observed native pass. It does not set the optional missing-host-native property, introduce an assumption/ignore, mock PDF rendering, or filter the host suite. The retired test's conditional host-only assumption escape is removed with the duplicate fixture. No production source changes.

## Assertion parity

| Retired host assertion/guard | Mandatory native equivalent |
| --- | --- |
| Exactly one provider query | `queries == 1` in probe; receipt checked by helper |
| Exactly one descriptor open | `opens == 1` in probe; receipt checked by helper |
| Reported document filename equals fixture | `document.name == ContinuumNativePdfProbeProvider.NAME` |
| Reported size equals fixture bytes | `document.sizeBytes == bytes().size.toLong()` |
| Exactly one page | `document.pageCount == 1`; receipt checked by helper |
| Text state is extracted | `document.textState == EXTRACTED` |
| Exactly one text page | `document.textPages == 1` |
| Extracted text contains synthetic marker | Same `CONTINUUM PDF TEST 4711` marker |
| First-page bitmap exists | `checkNotNull(document.firstPage)` |
| First-page width is 360 pixels | `bitmap.width == 360` |
| At least one opaque dark pixel | Same alpha/RGB predicate; `ink > 0` |
| Provider permits read mode only | Java provider rejects every mode except `r` |
| Bitmap is recycled after assertions | Native probe retains `finally` recycle |

The native probe additionally checks positive bitmap height and per-page text attribution. The retired file had one success case and no product negative-test cases.

## Host coverage retained

- `PdfDocumentIntakeTextTest`: all five text normalization, empty-text, budget and page-attribution tests unchanged (blob `e211794b59434736c8a1a7e9b1de73c9ed405db7`).
- `PdfIntakeCancellationUiTest`: cancellation before unavailable-provider access unchanged (blob `703ade50503a3b048829c66eb8fd73d07a6b3db9`).
- Existing PDF scene/dialogue tests unchanged.

## Completion rule

The full Code55 workflow requires both `full-android` (unfiltered JUnit/UI plus lint) and `native-pdf`; `complete-qa` fails if either fails or is skipped. This commit leaves all workflow logic and the native probe/helper unchanged. One native-decode case moves out of the host count; it remains compulsory as the native job.

The migrated integrated SHA must pass the complete workflow again. Emulator evidence proves Android native PDF processing only, not Fold7 live capture, GPU quality, audio, thermal performance or the full Argon4 scenario.
