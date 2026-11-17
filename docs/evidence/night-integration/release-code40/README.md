# Original-signed owner update — code40

**PUBLISHED_AND_INDEPENDENTLY_VERIFIED.** The existing publisher deployed source `0ff2cef550db8fc84c32690793921edf3a7830e9` as version **0.3.39 / versionCode40**. The actual downloaded APK was verified at **2026-11-03 23:54:19 UTC** (2026-11-04 00:54 CET).

[Download the verified APK](https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-416ff0a388df82532bea73dfb938a8985169d85688743527133be04ca8683d19.apk) · [Existing old-app catalog](https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json)

| Verified property | Result |
|---|---|
| Package | `at.heinz.deviceai.continuumlocal` |
| Version | `0.3.39`, code `40`; previous catalog code was `39` |
| Source | `0ff2cef550db8fc84c32690793921edf3a7830e9` |
| APK bytes | `84298233` |
| APK SHA-256 | `416ff0a388df82532bea73dfb938a8985169d85688743527133be04ca8683d19` |
| Original certificate SHA-256 | `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` |
| Actual signature check | `apksigner verify --verbose --print-certs`: exit0, one signer, APK signature scheme v2 verified |
| Native ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Physical installation | NOT_RUN; normal Android installation confirmation remains required |

## Evidence chain

1. Local clean-source Node, Android, backend, parity, lint and unsigned package gates passed. [Local receipt](../0ff2cef5/README.md) preserves original XML, explicit skips and the original failed runs separately.
2. The configured research publisher branch was fast-forwarded from `a9709854f48d2fd6589f9ce5ea97f6ec3be0d233` to the exact tested source using an expected-head lease and no force. Main was not merged or updated by this work.
3. The current owner's overnight request authorized an original-signed APK on the existing Download path. Only nonsecret source/version/host guard variables were merged into existing service configuration. The original protected signing material was not retrieved or replaced.
4. Updating those guards automatically started deployment `dep-dbl70arm8hqs73ch40pg` on `srv-dbf1tr2vcj2c73ehpfog`. No duplicate manual deployment was started. The existing fixed build command, publisher and autoDeploy setting were retained.
5. [Complete build log](render-build-log.json) records exact-source `CONTINUUM_OWNER_PRESIGN_QA_PASS` at23:50:08 UTC, successful signed build, and `CONTINUUM_OWNER_RENDER_SIGNED_STAGED_FOR_PINNED_HOST` at23:51:48 UTC. [Deployment metadata](render-deploy.json) records LIVE at23:51:49.880689 UTC. The existing release script required its own atomic, critical/full Node, exact-source receipt, Android/lint/unsigned and signer gates; no gate was disabled or substituted.
6. The readback fetched the existing strict twelve-field catalog without redirects, then downloaded its exact immutable APK URL. Actual bytes/length/SHA-256, ZIP integrity, package/version and the original signing certificate were checked independently. [Verification receipt](verification.json), [actual apksigner output](apksigner.txt), [actual APK badging](badging.txt), [live catalog bytes](after-manifest.json) and [HTTP response headers](http.json) preserve those results. [Readback script](verify-published-apk.py) is the actual external verification harness; it requires the same build-tools35 directory relative to its original build-env location.

The APK is not added to Git. The download above is the published immutable artifact. Documentation commits after0ff2cef5 record evidence and do not change the application source used for this release. The existing old-app updater/catalog source is byte-identical to code39, so the new strictly higher code40 is offered through the same Download button. This is a source/protocol/readback result, not a physical tap or installation on the owner's Fold7.

## Explicit remaining limits

No real model/S01 run was started during this integration. The earlier failed S01 and its unresolved crash/resource barrier remain unresolved and intact. Native autonomous coding acceptance, direct ChatGPT/MCP connectivity, Android ARM64 isolation, full physical Fold7 behavior/performance and exact native Filament/video likeness are not established by this publication. The independent service/MCP source and persistent bot UI are integrated; publication must not be reported as completion of those operating milestones.

Existing owner files were not accessed or modified by the work. The update code includes the documented conditional LEX3 migration and retention/classification behavior; see the [compatibility review](../0ff2cef5/README.md#existing-download-button-and-stored-data-compatibility). A future downgrade to code39 is not a promised reversal after that migration.

`publish-attempt.json` and the before/prepublish catalog files are historical pre-deploy observations. Their running/code39 values are preserved literally and superseded by the final deployment, catalog and readback evidence above. `MANIFEST.sha256` covers every file here except itself; no private signing key is included.
