# Owner face model integration — 2026-11-05

Status: **MODEL / CONFIGURATION HOST CHECKS PASS; FINAL APK AND DEVICE CHECKS PENDING**.

Source base: `6c4150190b0d3ad5a148a21a6808962b44be2a4d`.
Branch: `codex/continuum-owner-face-models-20261105`; draft PR #552.
Scope: pinned model/dependency build assets, licenses and explicit ARM64 packaging. Camera, recognition policy, profile storage and enrollment UI belong to the other integration tasks.
Relevant experiments: EXP-00 (reproducible Android build), EXP-05 (local inference), EXP-12 (owner identity continuity).

## Source checkpoints

| Local commit | Change / exact validation source |
| --- | --- |
| `f9b043cea7fd78484a85cafd32ccd5bbafc6014c` | OpenCV dependency, pinned model downloader, generated assets, initial licenses |
| `1ef7c417ee0e4d30435bef0b300971d1e8eda8ee` | Eleven downloader tests and rejection of unexpected generated assets; actual cold download passed |
| `26aa7896faec5205a4f8d3b9ad9565e83024fce6` | Explicit ARM64 property, lazy preBuild hook, corrected copyright notice; eight real Gradle configuration checks passed |
| `222f81fbde31e702bd823726022078410db10f42` | Exact LLVM libc++ license and pinned license hash; eleven model tests passed again on unchanged, clean source |

These are local commits. Connector publication can create different commit IDs with identical trees; the integration owner records the remote correspondence. A passing subset here does not certify the final combined source or final release pipeline.

## Fixed, downloaded inputs

The tracked lockfile is `app/owner-face-models.lock.json`. Both actual model binaries match the byte lengths and SHA-256 hashes in the pinned upstream Git LFS pointers. The downloaded AAR matches the official Maven Central checksum.

| Input | Bytes | SHA-256 |
| --- | ---: | --- |
| `org.opencv:opencv:4.13.0` AAR | 121695690 | `a8c9b0929d8d6367bb476320a1f36587db2b57ea18d8f7521ce509514903ca04` |
| `face_detection_yunet_2023mar.onnx` | 232589 | `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4` |
| `face_recognition_sface_2021dec.onnx` | 38696353 | `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79` |

OpenCV Zoo source is fixed to commit `48b10fa505aeb51fbd127fc60a5c660040e823b7`:

- [YuNet source and license](https://github.com/opencv/opencv_zoo/tree/48b10fa505aeb51fbd127fc60a5c660040e823b7/models/face_detection_yunet)
- [SFace source and license](https://github.com/opencv/opencv_zoo/tree/48b10fa505aeb51fbd127fc60a5c660040e823b7/models/face_recognition_sface)
- [OpenCV AAR and checksum](https://repo.maven.apache.org/maven2/org/opencv/opencv/4.13.0/)

Actual AAR `classes.jar` inspection with `javap` confirms `OpenCVLoader.initLocal`, `FaceDetectorYN.create/detect/setInputSize` and `FaceRecognizerSF.create/alignCrop/feature/match` are present. This establishes the dependency API, not Android runtime execution.

## Generated assets and failure behavior

`prepareOwnerFaceModels` runs before `preBuild`. It resolves the exact AAR, verifies its size/hash, and downloads verified model bytes into the Gradle user cache. HTTPS transfers inherit the normal environment proxy. Model output goes to `app/build/generated/owner-face-model-assets/faceid/`; runtime names are `faceid/` plus the original filenames. The generated `model-manifest.json` records the exact pins. Model weights and AAR binaries are not tracked in Git.

Both models are verified before either is staged. Hash or size mismatch, interrupted download, an unsafe symlink, overlapping output/cache directories, invalid manifest input or an unexpected generated asset stops the build. Valid cache reuse works without a network request. Text licenses remain normal source assets. The original package/signing/version/publication rules are unchanged.

Actual cold CLI download and independent output rehash passed on `1ef7c417`. Eleven targeted Node tests passed with zero failures/skips on clean, unchanged `222f81fb`: actual production pins/licenses, successful staging, offline cache reuse, same-size corruption, truncation, interrupted second transfer, altered AAR, corrupt-cache recovery, symlink rejection, unexpected output rejection, overlapping paths and invalid manifest values. Several assertions are grouped within those eleven cases.

## Explicit owner ARM64 packaging

`continuumOwnerArm64` accepts only literal `true` or `false`. Absent or `false` leaves the existing ABI defaults untouched. `true` adds only `arm64-v8a` and requires one of the three existing, mutually exclusive local modes: `continuumLocalStandalone`, `continuumLocalStandaloneQa` or `continuumLocalRenderUnsigned`. It does not authorize any additional task, signer, package or version.

The integration owner supplies `-PcontinuumOwnerArm64=true` locally and `ORG_GRADLE_PROJECT_continuumOwnerArm64=true` to the existing publisher. Both are the same Gradle property; no release-script fork is required.

Eight real AGP configuration checks ran sequentially with Gradle 8.11.1, JDK 17 and `--dry-run`, using proxy settings from that same execution environment. The init script asserted the configured ABI set, package, version and absent release signer. No APK or test signer was created. Source before and after was clean and identical at `26aa7896`.

| Configuration | Observed result |
| --- | --- |
| Generic default | PASS; empty ABI filters; original generic package/version |
| Unsigned local default | PASS; empty ABI filters; `at.heinz.deviceai.continuumlocal`, version 42 |
| Unsigned local explicit `false` | PASS; same defaults |
| Unsigned local env property `true` | PASS; exactly `arm64-v8a`, unchanged package/version, release signer null |
| Local QA CLI property `true` | PASS; exactly `arm64-v8a`; existing QA mode preserved |
| Generic mode with `true` | PASS; rejected by the new local-mode guard |
| Noncanonical `TRUE` | PASS; rejected by the strict boolean guard |
| Owner signed mode without credentials | PASS; rejected by the existing original-signer requirement |

A successful original signed build is still the publisher's responsibility. The missing-signer negative check does not substitute for it.

## License audit

All four complete license texts ship under `assets/faceid/`, with upstream attribution in `NOTICE.txt`.

| Component | License | License text SHA-256 |
| --- | --- | --- |
| YuNet | MIT | `c83b8120c50ccbd4c4f96edf53141bdd566ebb8f8e9227e415326aa1b1aba958` |
| SFace | Apache-2.0 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| OpenCV | Apache-2.0 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| Bundled libc++ | Apache-2.0 WITH LLVM-exception, with retained legacy notices | `539dd7aed86e8a4f12cbdd0e6c50c189c7d74847e4fecc64ce2c6ee3a01da38b` |

The two pinned model READMEs state that the license covers all files in their respective directory. The libc++ ELF `.comment` identifies Android LLVM source commit `d8003a456d14a3deb8054cdaa529ffbf02d9b262`; its [exact license text](https://android.googlesource.com/toolchain/llvm-project/+/d8003a456d14a3deb8054cdaa529ffbf02d9b262/libcxx/LICENSE.TXT) was downloaded, decoded and retained, including exceptions and legacy terms. No model weights or public test photograph were added to Git.

## Actual native sizes and APK estimate

The downloaded original live APK 41 is 84298233 bytes. Its actual signature/package checks passed: package `at.heinz.deviceai.continuumlocal`, versionCode 41 / versionName `0.3.40`, v2 signature, certificate SHA-256 `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`; APK SHA-256 `63de7696d36efce3b5cd353cb77e53b6f8d67e7b73a6c4da0a952b1c05223893`. This is the old live artifact, not the new candidate.

| ABI | Live APK 41 native ZIP bytes | New OpenCV JNI raw bytes | OpenCV AAR compressed bytes |
| --- | ---: | ---: | ---: |
| arm64-v8a | 29101560 | 25265448 | 9918685 |
| armeabi-v7a | 5905576 | 16683896 | 8211456 |
| x86 | 7443464 | 44808820 | 17715797 |
| x86_64 | 33604776 | 60544696 | 24831210 |

Live APK natives are ZIP_STORED: their compressed and raw sizes are identical. The AAR uses DEFLATE, so its compressed sizes are not a valid estimate for the final APK native entries. With the same stored-native packaging, the new model files add an estimated 36115080 compressed bytes (measured raw DEFLATE level 6). The current worktree GLB adds an estimated 2305169 compressed bytes over live APK 41.

These measured inputs give approximately 270021342 bytes (257.51 MiB) universal versus 101030114 bytes (96.35 MiB) with ARM64 only, before new DEX, ZIP alignment and other source changes. Removing the other native ABIs saves 168991228 bytes (161.16 MiB). These are estimates, not final APK measurements or a guaranteed size ceiling.

No 100 MB single-file APK limit was established from the reviewed [Render static-site documentation](https://render.com/docs/static-sites). The [build-pipeline documentation](https://render.com/docs/build-pipeline) describes build resources; its 16 GB disk figure is not a single-file serving limit. The ARM64 choice is justified by the measured size reduction for the requested Fold7 build, without asserting an unverified Render cap.

## 16 KB boundary

The actual OpenCV ARM64 `libopencv_java4.so` and `libc++_shared.so` both have AArch64 ELF headers and every PT_LOAD segment has `p_align = 16384` with congruent file offset and virtual address modulo 16384. Their hashes and every inspected segment are retained in the accompanying evidence JSON. This is a positive ELF alignment result for these two libraries.

It does not establish final APK ZIP alignment or execution on a 16 KB Android device. Following the [Android page-size guidance](https://developer.android.com/guide/practices/page-sizes) and [zipalign documentation](https://developer.android.com/tools/zipalign), the integration owner must inspect every packaged native library and run `zipalign -c -P 16 -v 4` on the actual generated APK. The final artifact audit also checks the only-arm64 ABI set, model pins, GLB hash, package/version and original v2 signer.

## Independent real host inference

A separate Linux x86_64 check used OpenCV Python 4.13.0.92 (runtime 4.13.0), the exact production models above and an official public OpenCV sample fixed to source commit `fe38fc608f6acb8b68953438a62305d8318f4fcd`. The downloaded wheel and image hashes are in the evidence JSON. Neither the image nor feature values are committed.

Observed: 512×512×3 input; YuNet returned 1×15 `float32`; `alignCrop` returned 112×112×3; SFace returned 1×128 `float32`; all outputs were finite. A black image produced zero detections. This confirms real host inference and the output shapes consumed by the recognition implementation. It does not test Android JNI initialization, Camera2 conversion, owner enrollment, recognition accuracy, liveness/spoof resistance or Fold7 behavior.

## Remaining final gates and evidence

The integration owner runs the combined source's full Node/Android tests, lint, unsigned ARM64 APK assembly and artifact checks. Original signed publication uses the existing publisher and existing private signer only after those source gates. A fresh live catalog read must precede version selection; the verified earlier live code was 41. New install/update behavior, actual enrollment and recognition, camera lifecycle, and 16 KB device execution require real-device observation.

Machine-readable evidence: `docs/research/omega/evidence/continuum-owner-face-models-20261105.json`. Raw local audit logs: `/workspace/scratch/19c84cfe59f9/build-env/face-model-audit/`, including `abi-property/receipt.json` and per-case logs, `model-tests-final.tap`, `prepare-real.log`, `opencv-arm64-16kb-audit.json`, and `host-inference/{receipt,provenance}.json`.

The earlier baseline full Node run on `6c415019` was 2360 PASS / 2 FAIL / 31 SKIP (2393 total); the two obsolete portrait source assertions were handed to the integration owner. That result remains source-specific and is not represented as a successful final release gate.

Rollback: revert only this branch's build integration/property before final source freeze. This task changed no enrollment data, original signing material, publisher environment or deployed APK.
