# APK-B6 — AAPT2 host preflight, 2026-11-05

**Status: READY_FOR_C1_REVIEW / TARGETED_CHECKS_GREEN / FULL_ANDROID_NOT_RUN.**

## Scope and source

Independent APK-support branch `support/continuum-apk-preflight-aapt2-20261105-b6`, pinned initial source #544 `6da09218472dc51b691bbe7996323fc84adb1dc8`. Its parent #543 is `89ae402d88b80ca5c48ab241e20c0369b7db517d` and contains the combined C1 head/Orb/Avatar/Neon preview, but has no new Android APK acceptance. The independently completed #547 Node run applies to #543 only. #550 handles the separate Orb overflow issue.

This support patch edits only `scripts/continuum-release-host-preflight.mjs` and its original `*.test.mjs`; it does not change the app, original package, signer, versions, release or downloader.

Original preflight Git blobs:
- production `c273b39943047b2b1e658eabe4c77646de9c77c8`
- tests `783cbb7bd26fcd2bb1b56d9ff32869e4318d9d35`

Patched Git blobs:
- production `e016ae6b91b377fbef6d0ff4698626ed15d3469c`
- tests `facbc36e01be5701f740d7c5671029f321409953`

## Reproduced defect and small correction

The old `inspectHostEnvironment` checked only whether an AAPT2 path existed. A plain text file, non-executable binary, wrong executable or failed subprocess could therefore pass the AAPT2 part of the preflight and mark a synthetic environment `hostReadyToAttemptQa=true`. That is not an Android compilation failure; it is a **false admission to attempt QA**. It is not proof an invalid binary was present on the owner's phone.

The patch runs the existing configured AAPT2 path with a bounded, shell-free `version` probe (5 s, 8 KiB), checks exit status and recognizes an AAPT2 2.x version banner from stdout/stderr. Missing paths retain `NATIVE_AAPT2_NOT_FOUND`; existing but unusable paths produce `NATIVE_AAPT2_INVALID`. The previous positive host fixture now provides a process fixture instead of a non-executable text file. The additional original-project test explicitly covers non-executable, exit-77, wrong tool and compatible version string. No executable is downloaded, signed or installed.

**Limit:** An executable stub can impersonate a version banner. This is a bounded process/compatibility gate, **not a cryptographic binary identity, Android ARM64 ABI verification or successful AAPT2/Gradle run**. The combined actual toolchain still requires genuine platform execution.

## Actual evidence

1. **Local real Node/FS/subprocess standalone probe:** `node --test --test-reporter=tap /mnt/data/continuum_apk_b6_20261105/aapt2-probe.mjs` — **6 PASS / 0 FAIL / 0 SKIP**, exit 0, Node 22.16.0. Real temporary files and shell processes; not the complete imported GitHub module. Original TAP: [aapt2-probe.tap](../../evidence/apk-b6-aapt2-20261105/aapt2-probe.tap), Git blob `7b4ce4168809a850818abf6f997730274da9e4f8`, SHA256 `9c7648577d69c4737cac9b424ff44ed50f5240a0ae7442e6c1e30b2011bf902f`.
2. **Exact published source-function check in V8 with stubbed fs/spawn:** 4 scenarios. On old `c273b399` all 3 faulty executable scenarios admitted; on patched `e016ae6` 3 rejected, 1 version-protocol fixture accepted; **4/4 checks PASS**, verified exact four pinned source-function extracts. This is not an actual child execution or whole-project test.
3. **Published script/test syntax:** two complete sources parsed with V8 after stripping only hashbang/module wrappers. **2/2 parse PASS**.
4. **New actual repository test:** committed under the original test path, **NOT_RUN** because there is no complete checkout with the pinned source and Node dependencies in this executor. Previous tests on other commits must not be transferred.

[Machine-readable full boundary and provenance](../../evidence/apk-b6-aapt2-20261105/RESULT.json).

## Build prerequisites and separate acceptance

Local executor currently has Node 22.16, Java/Javac 21.0.12.1, Kotlin 1.9.0; no configured JDK17, Gradle8.11.1, SDK36, native AAPT2/ADB or complete app checkout. **This session ran no `:app:assembleRelease`, no actual Android unit/UI, Lint, backend, GPU, audio or Fold7 test; APK NOT_RUN**. #526's successful debug QA APK and #527's 3032 Android PASS are historical on other sources; neither makes the new owner package installable.

The `#515` separate branch already strengthens the preflight's **file-shape** admission and Git source audit on a different base. C1 must merge its validated `availableFile` safeguards **before or with** this new version probe, never replace them with the older base excerpt. Keep its scheduler/branch work unaffected.

## Next step and rollback

C1/QA integrator: (1) read exact current #544/#515/C1 heads and path preimages; (2) selectively preserve both validation layers on a pinned combined source; (3) execute the **actual** `node --test scripts/continuum-release-host-preflight.test.mjs` (including all original tests); (4) if green, run full critical/full Node, backend, entire Android unit/UI without filters, configured Lint, source-bound real AAPT2/ABI and original unsigned build check; (5) require explicit owner SHA/freeze approval, original signing identity, version > verified live code and HTTPS original-host readback before publication. C2/physical Fold7 acceptance separate.

Rollback: revert only this branch's two-path probe change, leave parent C1/#544, #515, owner files, signer, model weights, offline switch and all historical artifacts intact. No Actions, paid service, main merge, signing, deployment or installation.
