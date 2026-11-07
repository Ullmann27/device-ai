# Continuum – owner integration APK candidate (02.11.2026)

**Status:** INTEGRATION_SOURCE_ONLY / APK_NOT_BUILT / SIGNATURE_NOT_VERIFIED / FOLD7_NOT_RUN.

## Mainline and dependency chain

- Protected main and installed owner APK are unchanged. This branch derives from GPT-6 PR359 (`83d3808b217aa0ecf3ff2739f3d4e22ce008cec8`), which already contains the ancestry of CodeRabbit PR354, Sol S1–S3 PR357, and Sol's independent Brain IV audit PR358.
- Prior Synapse II/III and Grand Challenge research is **already ancestral**, proven by GitHub compare `ahead_by=0` vs PR359; do not duplicate source or infer all experiments are product-enabled.
- Owner-pinned, still optional GGUF/Termux research PR356 (`d85810508744ce61a9092a1ac531fc4fdf5e843c`) is merged to this branch through a real two-parent Git merge commit `bdb5da4abc0249b8b737af4fcc28977c9700735a`: exactly 6 previously isolated GGUF research files, one is the existing Termux launcher. This **does not activate** any alternate model in the Android runtime.
- Experimental 3D avatar upstream intake PR350 is **not** an Android avatar. Preserving licensed source references does not justify claiming a working animated head.
- PR337 @ `56e9a82fde0062db9a7daa8cfb16840a6615380d`: current Activity already contains every scroll/deadline hunk (`git apply --reverse --check` PASS), and its policy, Kotlin tests and Node contract are byte-identical. No second UI patch is needed. This is source evidence, not a new Fold7 test.
- Real native click PR354 is **not** yet registered for owner-approved live actions; do not silently enable it.
- New PR359 Gitar review reported 2 closed and CodeRabbit's latest exact-head review raised further actionable blockers, especially durable UNKNOWN replay, LiteRT tool continuation budget and full QA receipt freshness. No unconditional production sign/release before these are solved.

## Acceptance/Render APK requirements

1. Review and repair current PR359 blockers; run full exact-source Kotlin/Gradle/Android lint, Node critical/full, and new GGUF tests on the complete checkout. Logs, exit codes, source commit and device/host profile are mandatory.
2. `scripts/render-continuum-integration-qa.sh` runs compilation/JUnit/lint with the isolated `.qa` identity, then assembles the **unsigned original package**, code38/version0.3.37, for pre-sign QA. Neither artifact is an authorized installable owner update. `scripts/render-continuum-owner-v38-release.sh` is a separate owner-signing/staging recipe requiring the pinned publisher, original key, exact-SHA approval and release-freeze lift; it stages `approved:false` pending device acceptance.
3. If later an owner **in-place upgrade** is approved, use the original existing signing P12, exact package `at.heinz.deviceai.continuumlocal`, a strictly higher versionCode than the currently installed app, pinned original publisher URL and both SHA-256/source/Android manifest verification. Never use an improvised debug key for that package.
4. Render workspace: `tea-d7qe6dosfn5c73e2rnag`. Existing Render static QA services are tied to specific historical branches. Do not redeploy them as evidence for this new source SHA; do not overwrite another agent's service or create a paid new instance without a cost decision. A real Render deployment for **this exact integration ref** must be confirmed before giving a download URL.
5. APK download button/link: provide only the verified HTTPS URL for an actually published artifact with an inspected package, signer, checksum and successful build, never a made-up link.

**Actionable next command on authorized QA host:** check out exact integration SHA, run `node --test scripts/continuum-atomic-acceptance.test.mjs`, then `bash scripts/continuum-atomic-acceptance.sh --expected-head "$(git rev-parse HEAD)" --out "/tmp/continuum-integration-$(git rev-parse --short=12 HEAD)"`. Full Gradle + Node acceptance currently **NOT_RUN** for this new merge.
