# Code55 historical candidate 75306783

**Superseded for release:** review identified app-topic conversation interception. Corrected source `889e06d47fffd2230fa5b062dd1f31b65992e728` is in [PR #827](https://github.com/Ullmann27/device-ai/pull/827), with [focused run43372191194](https://github.com/Ullmann27/device-ai/actions/runs/43372191194). Do not sign75306783; final full QA/APK must certify the replacement.

Frozen source: `753067835c3b33ff850f5c3e8d079d9dcd5eb742`; tree `ea717d3aaf18b5c6023fe86b899d5519e7c2764e`.
Canonical branch: `integrate/code55-apk-fable-20261112`, PR #809. No main merge.

[Focused preflight43365355774](https://github.com/Ullmann27/device-ai/actions/runs/43365355774) passed on that exact SHA: 376 Android/UI cases, zero failures/errors/skips; full Node2474 passed/37 known skips and backend64 passed. The four real Activity visual scenes and all nine visibility cases passed. Critical137 is a subset of full Node.

[Complete QA43367109818](https://github.com/Ullmann27/device-ai/actions/runs/43367109818): **PASS**, including all lanes and lint. Unfiltered Android/UI: 5,383 cases in 894 suites; 5,366 passed, zero failures/errors, 17 explicit known skips. No unknown skips. This does not certify the newer dialogue correction.

[APK43367109797](https://github.com/Ullmann27/device-ai/actions/runs/43367109797) passed: ARM64 assembly, original assets/package/16KiB audit and 481 scoped Android tests (65 suites, zero failures/errors/skips). APK SHA256 `4f7f0e9be17ac08cc90cb0f08d95ba6ad87aa495897d3d4512de61c462c5f18c`, 103,787,127 bytes. **UNSIGNED_NOT_INSTALLABLE**. All five artifact archives were independently downloaded and verified against GitHub SHA256/size metadata; APK bytes and 43 asset hashes agree with the audit. Original signing, installation, Fold7 live capture/action/voice and performance/thermal acceptance are NOT_RUN.

Machine-readable metadata: [preflight.json](preflight.json), [verification.json](verification.json), and [download-verification.json](download-verification.json). Download verification is artifact inspection, not another execution of the test suites.

These receipts are committed on an evidence-only branch so recording results does not silently alter the source under test. Do not fast-forward the release candidate to this branch before signing; doing so would require new full exact-source QA and APK.

Independent full-archive audit: `full-download-verification.json`. The 315,552,160-byte ZIP matches GitHub SHA256, CRC and safe-path limits. Recomputed 894 JUnit files agree with the job report. Its 608 PNGs contain host UI/fallback evidence, not proof of final native scene appearance; no MP4 is present. Inspected normal phone and Fold layouts plus 200% font. The 200% host fallback header overlaps its action buttons and navigation captions truncate; this is not accepted as a finished accessibility/visual result. Native full-scene appearance needs a separate pixel capture and Fold7 inspection.
