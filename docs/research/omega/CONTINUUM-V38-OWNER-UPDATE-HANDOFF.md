# Continuum – Original-App Integration v38 – Handoff, 02.11.2026

## Owner objective and source

The owner explicitly requested an **in-place update** of installed Continuum Lokal, not a separate QA app. The original package is `at.heinz.deviceai.continuumlocal`; proposed new versionCode is `38` / versionName `0.3.37`. The original owner-signing certificate SHA-256 is `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`. Neither `main` nor the installed app has been modified by this branch.

Integration Draft PR [#360](https://github.com/Ullmann27/device-ai/pull/360), branch `integration/continuum-all-systems-apk-qa-20261102`, forks PR359 and thus inherits Sol S1–S3, PR358 research, CodeRabbit PR354, Synapse and Grand Challenge. Two-parent Git merges additionally joined the PR356 GGUF files and the licensed PR350 avatar **research material**, not a live alternative Android model or an animated avatar. Do not describe every research file as a product feature.

Experiment contract: `EXP-01` full system compilation, `EXP-02` QA/test boundary, `EXP-05` model runtime research and `EXP-12` autonomous tool safety, per `docs/CONTINUUM-EXPERIMENTE.json`.

## Real Render build and provenance

- Dedicated free-plan integration QA service `srv-dbkbtgqjnfac73a6incg`. Build source branch is this **draft** integration branch, Node 22.16.0, pinned Temurin 17.0.20.1+1 and SHA-verified Android command line tools + Gradle 8.11.1.
- Test-only script: `scripts/render-continuum-integration-qa.sh`. Runs special Node/GGUF tests; canonical critical and full Node suite; **all Android Gradle compilation, JUnit tests and lint**; separate unsigned **original-package** release assembly. Source SHA, package, version and unsigned digest must match. Errors are accumulated rather than hiding Android test results behind the first historical Node error. **Any failed/missing receipt causes fail and prevents release.** Never use this QA server to serve a signed or unsigned installable APK.
- Verified historical Node blocker: Render's Git checkout has **no `origin` remote and lacks commit `5270202e0699764b0eac5d4dd3e6a587b982ae05`**. The historical v25 security suite originally failed before all tests due to unavailable source. The authentic manifest `docs/release/continuum-0324-live-source-proposal-20261027.json` is still pinned to Git blob `441e667cb1c8b48106e1227df0c50a37b5b53399`, identifying exactly 1,095 historical files.
- **Non-fake reconstruction:** audited all 1,095 manifest blobs against the original historical Git tree `4a07b51f38e10010d05bb37850a0cf62cf8fd663` (zero missing/mismatched) and included only those genuine blobs (approximately 11.0 MiB uncompressed source) in `docs/evidence/continuum-v25-frozen-source/`, immutable subtree SHA **`87e4c52821c4d5b5c61d4bab912b1dbe2aec1c25`**. `scripts/continuum-0324-live-source-gate.test.mjs` now uses that subtree **only when the old Git commit is absent**. It compares the exact subtree Git hash and individually recomputes Git blob SHA-1 for all 1,095 files before running the same original protocol tests. It still fails closed on missing, redirected or modified source. This is not synthetic success.
- Earlier first complete Android JUnit run on integration commit `ff5226b6f3da29eada7aef070f1d62b4a95630a2`: **1,666 tests executed, 1 FAILED, 3 skipped**. Failure: `LocalPromptFrameTest.modelAdvisoryCannotFitIfItWouldStealOwnerBudget` at line 89 mistakenly used UTF-16 character count instead of UTF-8 byte count for a German owner prompt containing an umlaut. Corrected its expected byte boundary in commit `c22011606e329ee8e8fe24bc591c7937ac87a46e`. This correction has **not yet been confirmed GREEN by a finished Render build**.
- Current real Render after frozen fallback: deploy `dep-dbkdilcipgvc73c99oq0` was started on source commit `14e950703c3eddf92bc95ff291898ada648f18dd`. **Read live Render state again before asserting PASS or release.** Changes after that commit require a fresh deployment for exactly the latest source SHA.
- The previous full Node failure was due to legacy test before-hook, not evidence of failed neural inference. Current new full Node/frozen test and Android Gradle combined status must still be collected.

## Safe original-signature release contract – WIP, NOT_APPROVED

`scripts/render-continuum-owner-v38-release.sh` is a **WIP reference recipe, not yet authorized to run or deployed**. It is derived from the existing owner-signed release flow and must ONLY execute on the historically pinned Render publisher service `continuum-owner-v23-release-preflight` with `CONTINUUM_RELEASE_HOST=continuum-owner-v23-release-preflight.onrender.com`.

Required separate release authorization: `CONTINUUM_RELEASE_APPROVED_SHA` equals **exact final source SHA**, `CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER` also equals that SHA, `CONTINUUM_RELEASE_VERSION_CODE=38`, the original owner P12 + password already held in the pinned service, plus successful exact-source full QA before signing. No credentials in GitHub sources, public logs or chat.

It checks P12 alias `continuum-local`, private-key entry, signer SHA-256, v2 signature, package identity, exact version, APK digest and staged readback. It **cannot** sign while the P0 release freeze remains active. Critically, the staged catalog has `approved:false` and `status:SIGNED_STAGED_DEVICE_NOT_RUN` until the owner performs a separate Fold7 acceptance and explicitly approves release publication. Do not turn this into an automatic updater release or assume that the downloaded APK will install.

The current PR #360 includes signed-release preparation only; no signed deployment was triggered. The original pinned publisher and installed owner APK remain unchanged.

## Remaining stop conditions, next steps and rollback

1. Recheck latest integration HEAD and Render results; rerun full Node + full exact-source Gradle/JUnit/lint/GGUF, preserving all failed logs and exit codes, and address all review findings without downgrading security.
2. Resolve PR359 critical durable post-commit UNKNOWN and LiteRT tool continuation limits before enabling native click; the integrated PR354 native click is not an authorization to change device UI.
3. Independent review and full test of the v38 signer/reference flow and update manifest; **owner freeze lift and exact SHA approval must be explicit**. No prior QA report may be reused after another commit.
4. If genuine original-signed owner candidate is eventually available, verify package/signature SHA-256 and Android package-manager update over versionCode37 **without uninstalling** (owner's live data must survive), then field-test chat/cancellation/voice/app actions and confirm Fold7 acceptance before catalog approved=true.
5. Rollback: keep Draft PR #360 unmerged, leave `main` untouched, preserve existing installed APK, do not overwrite historical signed release. Failures produce logs/receipts, not silent merge or rollback of owner data.

**Current status: SOURCE_INTEGRATED; historical fixture blob-proof verified through GitHub; old Android JUnit RED 1/1666; latest complete Android/Node GREEN NOT YET VERIFIED; SIGNED_APK NOT_BUILT; DOWNLOAD NOT_AVAILABLE; FOLD7 NOT_RUN.**
