# Continuum 0.3.24 / code25 — integrated live release readiness (27 October 2026)

**STATE: SOURCE_INTEGRATED / QA_NOT_RUN / OWNER_SIGNING_BLOCKED / NO_APK_PUBLISHED.**

Official repository: https://github.com/Ullmann27/device-ai
Review branch: `integration/continuum-owner-v26-live-candidate-20261027`.
Note: `v26` in the *research branch name* denotes the next integration review; **Android version is still exactly 0.3.24, versionCode 25**. Do not install a second package.

## Exact integrated ancestry and change origin

Source integration began from UI PR #186 `be62d1c62b0ad27cbcdc9e28f445dfafa354ce18` (18 UI commits) whose parent contains PR #183 `029ba12c2b92a8fc52025b0a557e9baaa4e3a153`, its cognitive PR #182 `8695f28bc15e26207c1908845a7e96c089cc2033`, and the earlier local-worker/ToolGate/Shizuku research lineage. The three divergent PR #185 files were integrated **byte-identically** as Git blobs by Git tree/commit, not reimplemented: `.github/continuum/coding-provider.mjs` = `f83d40f7417199883bf55fb0bf5462ebecf87239`; `.github/continuum/coding-outcome-journal.test.mjs` = `a2915cc29538f25f84944006fe5da403669a9104`; `docs/research/omega/GPT6-CODING-OUTCOME-JOURNAL-20261027.md` = `1c303224b245ad95a305279393da927c6e4b49a4`. Integration commit `7732de4ffa3ec758ca7ff2b37562a94f2619687f`. Parents' branches not updated or merged; `main` stays untouched.

**Integrated source areas (capability, not yet a mobile acceptance):**
- Upright unfolded Fold7 UI, larger orb + 6 controls and six equally sized dashboard cells: PR #186. A real pixel match is NOT established without owner-device screenshot.
- Narrow typed conversation corrections, grounding-context reset, two read-only tool steps, explicit owner-memory safety: inherited PR #182. Developer-authored offline 12/12 vs 4/12 is NOT neural model improvement or on-device proof.
- Bounded DeepSeek Harness SDK/CCC1 coding research and encrypted same-host two-process storage prototype: inherited PR #183. Prior actual local Qwen 1.5B model repairs FAILED; do not enable autonomous patch application. These host-only research capabilities are **not** independently confirmed to run natively on the phone.
- Durable coding-provider failure/timeout/invalid-output classification: copied verbatim from PR #185. New native regression source needs actual execution.

## New independent build/QA lane (never uses owner key)

File: `scripts/continuum-owner-current-v25-qa.sh`.
It checks clean source, installs pinned Gradle and Android SDK/JDK prerequisites, runs Node/Backend/Android unit/lint/build, signs a **non-owner QA artifact** with an independently generated disposable keystore, verifies app package/version AND verifies its certificate is NOT the owner certificate. It exposes only logs/metadata in `continuum-owner-current-v25-qa/`; **does not publish an APK**. This artifact is NOT safe to install over the owner app.

The previous `scripts/continuum-v25-owner-release.test.mjs` applies to **old `db23e9...` / 1034-blob inventory only**; that frozen old release test is excluded from the new full QA worker test selection, explicitly recorded in `TEST-SCOPE.txt`. It is not edited. The old release gate still must fail closed on the new tree, and the **new** `scripts/continuum-0324-live-source-gate.test.mjs` tests the new source inventory separately. Historical tests belong to their own release source and may not be relabeled green for a changed tree.

The exact commands from an authorized CHECKOUT of the current branch:

```bash
node --test scripts/continuum-0324-live-source-gate.test.mjs
node --test .github/continuum/coding-outcome-journal.test.mjs .github/continuum/coding-provider.test.mjs .github/continuum/fold7-portrait-reference.test.mjs
bash scripts/continuum-owner-current-v25-qa.sh
```

A Render QA static site requires **branch** = `integration/continuum-owner-v26-live-candidate-20261027`, **buildCommand** = `bash scripts/continuum-owner-current-v25-qa.sh`, **publishPath** = `continuum-owner-current-v25-qa`. AutoDeploy should be OFF. Do NOT repoint the existing signed-APK publisher until QA and a separate owner-source approval complete. Current Render connector reads existing services/builds but exposes no service branch/build-command mutation tool; this run was NOT triggered and no credentials were viewed or changed.

## Source-freeze proposal and non-bypassable signing review

`docs/release/continuum-0324-live-source-proposal-20261027.json` is an **unapproved** immutable candidate file inventory. It pins exactly **1095** real executable/input Git blobs against `sourceHead=39008eaffb42a092b40aebd295df6b66f2cada61`, versionCode=25, versionName=0.3.24, unchanged package `at.heinz.deviceai.mentorpaired` and historical owner signer certificate SHA256 `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`. Manifest git blob: `402e8e24cb514627802ce7cf5b64db52b989ec7c`.

`scripts/continuum-0324-live-source-gate.mjs` is a separate read-only, **fail-closed** source verifier requiring all of the owner's explicit approvals: `CONTINUUM_OWNER_0324_LIVE_APPROVED_SOURCE_SHA`, `CONTINUUM_OWNER_0324_LIVE_APPROVED_MANIFEST_BLOB`, `CONTINUUM_RELEASE_SHA` and `CONTINUUM_OWNER_0324_LIVE_APPROVED_BUILDER_SHA`, plus all exact source blobs and no unexpected executable files. The new source gate and its test are builder paths (pinned by the eventual exact approved builder HEAD) excluded from pre-existing source manifest only because they did not exist at source freeze. The old v25 signed release and original source gate are **untouched**.

GitHub Git-tree verification at this checkpoint: **1095/1095 source blobs match** the published file inventory; **0 mismatches**. The only executable files outside that inventory are the two documented gate/test builder files; both are explicitly excluded by name and remain subject to future exact builder SHA approval. This is a **static Git-object integrity check**, not a run of the Node/Gradle tests or owner approval.

## New completed source work after the initial integration report

A **separate, opt-in real owner-signing candidate** is now committed at `scripts/render-mentor-owner-update-v25-live.sh`. It is derived from the previously reviewed v25 owner signing recipe, preserves the old owner signing certificate, package, signing checks, Android release versionCode 25, three rollback APK attestations and old v22 historical source audit. The original 2026-10-26 builder and Render release service were **NOT modified**. The new candidate calls `scripts/continuum-0324-live-source-gate.mjs` to verify all **1095** immutable source blobs, plus the explicit approved final builder HEAD, and requires a SHA256-byte-pinned successful **non-owner QA STATUS.txt** for **that exact builder commit** before the owner key can be decoded. Required additional owner-supplied environment values are `CONTINUUM_OWNER_0324_LIVE_QA_STATUS_URL` and `CONTINUUM_OWNER_0324_LIVE_APPROVED_QA_STATUS_SHA256`; these are **not supplied, inferred or set** now. The QA evidence must include exact source SHA, code25/version0.3.24/package identity, successful non-owner build, and explicit `owner_installable=FALSE` / `release_authorized=FALSE`. Neither a simulated model test nor a historical Render build substitutes for this proof.

The **native test source** `scripts/continuum-0324-live-source-gate.test.mjs` now includes three further negative/syntax tests of this signing recipe: `bash -n`, no owner approval with dummy credentials, and a synthetically matching builder without owner inventory authorization. They are committed, but native Node/Bash execution is **NOT_RUN**. A double-escaped URL regex/checksum format that would have rejected valid QA evidence was discovered by inspection, reproducing rejection in a separate Bash snippet and fixed before release; no signed builder was executed. All new post-freeze scripts are within the separately exact-final-HEAD-approved builder scope.

**Render operational status verified this turn:** existing *non-signing QA service* `srv-dbeeobivcj2c73cnpcdg` remains configured on old branch `integration/continuum-owner-0323-review-20261026`, old QA command `bash scripts/continuum-owner-v24-build-qa.sh`, old output directory `continuum-owner-v24-qa`, autoDeploy OFF. The owner-signed APK publisher `srv-dbaue53bc2fs73crkop0` remains on old `release/continuum-owner-v24-candidate-20261026` and untouched. Render connector permits inspecting and triggering but not editing a service's branch/build command/publish path. An owner-authorized browser update attempt was **not started** because the connected TinyFish automation wallet was insufficient (negative balance); no Render changes or build occurred. The only safe next Render action is to repoint the existing QA service's THREE settings to this branch / `bash scripts/continuum-owner-current-v25-qa.sh` / `continuum-owner-current-v25-qa`, maintain autoDeploy OFF, then trigger the QA service once and inspect raw logs. DO NOT trigger the original signed publisher.

## Remaining release blockers

1. Execute the real native Node/Backend/Android QA command above on this exact review branch (source may need corrections). Confirm Kotlin compiler, JUnit, Gradle, lint; capture exit codes, raw logs and artifact SHA. The 2026-10-26 success on the *old v24 code24 source* is not transferable to this candidate.
2. Independent code/security review: verify source ancestry, 3 copied PR185 blobs, normal Kotlin portrait rendering, non-regression on Fold cover and landscape, no double model calls, no ToolGate security regression or faux learning labels. Record and fix all concrete failures on a new revision; **every source fix invalidates the current source SHA/blobs and requires re-freezing the manifest**.
3. Review and separately authorize the exact immutable **post-QA** source tree, inventory SHA and final **release-builder commit**. Old hardcoded `db23e9...` gate must NEVER be repurposed to sign new code. Existing Render signer credentials/key/cert must be reused unchanged, never rotated.
4. Construct or review the new approved owner-signed release recipe from the existing `scripts/render-mentor-owner-update-v25.sh` plus the new source gate. The genuine owner package identity and strictly higher versionCode=25 must be checked using `aapt`; verify **actual** owner certificate fingerprint and SHA256 of the output with `apksigner`. Preserve current publicly hosted signed 0.3.23/code24 and earlier rollback APKs. The QA-only artifact cannot substitute.
5. On a real unfolded Fold7: run visual snapshot export, compare with the owner's 1144x1375 reference through `scripts/continuum-fold7-portrait-compare.py`, test portrait/landscape/cover controls, check no crash and data survives in-place update. If not pixel-accurate, correct and rebuild with a newly approved source proposal.

**Status at report generation:** No QA command executed on the new integrated HEAD; no owner signing, deploy, APK or device install. Delivering a fake download would violate owner update compatibility. Source tree and open PR remain available for direct follow-up.
