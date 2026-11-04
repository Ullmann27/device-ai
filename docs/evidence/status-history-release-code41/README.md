# Status-screen repair: original code 41 published

**PUBLISHED_AND_INDEPENDENTLY_VERIFIED.** The existing original publisher delivered exact application source `a8e58ae09f6f1a8f43bd0a6f3b30b7e81bb79be6` as **0.3.40 / versionCode 41**. Render became LIVE at **2026-11-04T04:51:07.038382Z**; the actual downloaded APK verification completed at **2026-11-04T04:51:48.371859+00:00**.

[Download the verified APK](https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-63de7696d36efce3b5cd353cb77e53b6f8d67e7b73a6c4da0a952b1c05223893.apk) · [Existing Download-button catalog](https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json) · [PR #475](https://github.com/Ullmann27/device-ai/pull/475)

| Verified property | Actual result |
|---|---|
| Package | `at.heinz.deviceai.continuumlocal` |
| Version | `0.3.40`, code `41`; prior live catalog was code `40` |
| Source | `a8e58ae09f6f1a8f43bd0a6f3b30b7e81bb79be6` |
| APK bytes | `84298233` |
| APK SHA-256 | `63de7696d36efce3b5cd353cb77e53b6f8d67e7b73a6c4da0a952b1c05223893` |
| Original signer SHA-256 | `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` |
| Actual signature verification | apksigner exit 0, one expected signer, APK signature scheme v2 verified |
| Native ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Code 41 physical device installation | NOT_RUN; Android confirmation remains required |

## What changed

Only `ContinuumBentoCockpit.kt` and `ContinuumControlsTest.kt` changed in application/test sources relative to published code 40. The fix preserves whole clock times and standalone URLs in status cards and recognizes complete existing interruption/deferral events. Stored state, model, scheduler, permissions, backend configuration, signature policy and updater sources are unchanged.

[Original targeted RED/GREEN evidence](../status-history-20261104/README.md) records 3 failed regressions before the fix, then the full controls class 10 passed / 0 failed / 0 skipped; source wiring 29 passed / 0 failed / 0 skipped; configured NewApi lint 0 issues. The native host captures use synthetic data. [Status meaning review](../status-history-20261104/status-semantics-review.md) and [dashboard metric review](../status-history-20261104/dashboard-metrics-review.md) explain the owner's actual screenshots without claiming additional model ability.

## Publisher evidence

1. The owner had already requested autonomous repairs and original-signed delivery through the existing Download button. The private publisher branch was fast-forwarded from `0ff2cef550db8fc84c32690793921edf3a7830e9` to the exact tested source using an expected-head lease, without force or a main merge.
2. The existing nonsecret source/version/host guards were merged without reading or replacing protected signer material. This automatically started deployment `dep-dblbct6gekts73fte14g`. No duplicate manual deployment was triggered. The build command, service, hostname, signing path and autoDeploy setting were retained.
3. The unchanged publisher required its atomic/critical/full Node checks, source-bound receipts, fresh Android/widget/lint checks, original-package unsigned APK/AAB and original signing gates. [Complete build log](render-build-log.json) records the required Android suites green at 04:46:58 UTC; full pre-sign QA pass for this exact source/code at 04:49:28 UTC; successful signed release build at 04:51:04 UTC; catalog compatibility and signed staging at 04:51:05 UTC. No gate was disabled or substituted. Exact full-suite numeric totals from code 40 are not presented as new code 41 totals.
4. [Deployment metadata](render-deploy.json) records the actual LIVE state. The independent verifier then fetched the strict twelve-field catalog without redirects and downloaded its immutable APK URL. SHA-256, byte count, ZIP integrity, package/version and the actual original certificate all passed.

Raw verification evidence: [receipt](verification.json), [live catalog](after-manifest.json), [actual apksigner output](apksigner.txt), [actual APK badging](badging.txt), [HTTP response headers](http.json), and [executed verifier](verify-published-apk.py). The verifier's original execution location is build-env beside the pinned build-tools 35 installation. The APK itself is published at the immutable download above and is not added to Git.

## Limits and historical records

The owner screenshots show code 40 opened on the real device and report readiness; they do not constitute a complete Fold7, audio/GPU/thermal or autonomous task acceptance. Code 41 was downloaded and verified on the host, not installed on the device by this work.

The consumed failed S01 remains unresolved and was not replayed. The source integration does not establish direct ChatGPT/MCP connectivity, successful new unknown coding tasks or autonomous Android ARM64 build/isolation. This display fix does not improve or benchmark Gemma. The preceding code 40 learning-store migration/downgrade boundary remains applicable.

`publish-attempt.json` and `prepublish-manifest.json` preserve the original in-progress/code 40 observations literally; they are superseded by the final receipt above. Documentation commits after the application source only record evidence and do not change the APK source. `MANIFEST.sha256` covers every file here except itself.
