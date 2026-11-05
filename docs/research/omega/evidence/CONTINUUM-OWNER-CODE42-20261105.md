# Continuum Lokal code42 — actual release acceptance

Status: **ORIGINAL_SIGNED_APK_PUBLISHED_AND_DOWNLOADED_VERIFIED**.

[Download the verified APK](https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-a1d56eb87b9224df383cdf9368c0406302c28e36d43ce9b2c4e16a5283be0efe.apk)

| Property | Verified value |
| --- | --- |
| App package | `at.heinz.deviceai.continuumlocal` |
| Version | `0.3.41` / code `42` |
| Source | `8a903fbebe605716b6163da35e1c3b2129af40a4` |
| APK length | `101712663` bytes |
| APK SHA-256 | `a1d56eb87b9224df383cdf9368c0406302c28e36d43ce9b2c4e16a5283be0efe` |
| Original certificate SHA-256 | `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` |
| ABI | `arm64-v8a` only |
| Deploy | `dep-dbm43j6pb74c73998hk0`; live `2026-11-05T09:10:54.211678Z` |

## Actual verification

The original publisher completed its strict same-source Node, Android, lint,
unsigned-package and original-signature release gates. Its success marker identifies
exactly the source and code42 above. The served catalog and actual APK were then
downloaded independently. `apksigner` confirmed the original single signer and
valid v2 signature. `aapt2` confirmed package/version, minSdk26 and targetSdk36.
Every packaged native library has compatible 16KB ELF LOAD alignment, and the
APK passed `zipalign -c -P 16 -v 4`.

Both ONNX models match their pinned sizes and hashes. The generated manifest
matches the source lockfile. All four licenses and NOTICE match source bytes.
The original avatar GLB remains 12038416 bytes with SHA-256
`c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.

Independent local source gates were Android3133 PASS / 0 FAIL / 19 SKIP,
Node2373 PASS / 0 FAIL / 31 SKIP, backend64 PASS / 0 FAIL / 0 SKIP and
zero reported debug-lint issues. The published source differs from that tested
implementation tree only in documentation. The publisher reran its gates on the
exact published commit. Skipped tests are not claimed as passes.

## Corrected publisher runtime

The first attempt `dep-dbm411t6e07s73b38m30` was correctly blocked: Render selected
Node26.13.0 from the unbounded `>=20` package range, while the project admits
Node20/22/24. Two tests reported `UNSUPPORTED_NODE_LTS`; the full-Node result and
its validation gate stayed red. Android, lint and unsigned assembly finished,
but no APK was signed or published from that attempt.

The existing service now pins `NODE_VERSION=24.22.1`, the actual runtime used by
the successful local tests. No source guard or test was weakened. The replacement
attempt above passed and published the unchanged app source. The existing source
approval, release-freeze selectors, original private signer and owner ARM64
property remain in the existing publisher; no new service or key was created.

## First personal comparison

Install the APK as an update over the existing Continuum Lokal app. At the camera
question, choose **MICH EINLERNEN**, then **Gesicht einlernen**, enter the display
name and explicitly permit the requested camera/enrollment session. After a
successful enrollment, choose **Mit meiner Vorlage vergleichen**. **Zum Gespräch**
can continue that explicitly approved comparison with the visible stop control.

A face template must first be enrolled on the actual device. No private face image
or embedding was included in source or the release evidence. Face comparison is
not authentication and grants no tool permission.

## Boundaries

This receipt establishes published bytes, source-gate results and original
update identity. It does not establish an actual installation on the Fold7,
Heinz's enrollment, recognition accuracy, audible voice quality or native
Filament rendering on the phone. The on-device camera/audio/GPU check remains
separate from the successful host build and artifact verification.

The JSON beside this file contains the actual binary audit and publisher markers.
The app release commit remains fixed; this evidence branch adds documentation only.
