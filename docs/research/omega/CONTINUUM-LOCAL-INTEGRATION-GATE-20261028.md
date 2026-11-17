# Continuum Lokal – first integrated candidate and release-gate handoff

**Date:** 2026-10-28. **Status:** review-only; existing Android app, owner files, Gemma model, SWB1 weights, signing and production branches untouched.

## Verified branch selection – do not merge the old app by accident

- **True current default `main`:** `190f5eabaa60fc59292b2b80d3086aafd7743a80`. Its `app/build.gradle.kts` has package `at.heinz.deviceai`, version `0.3.0` / versionCode `3`, not the installed `at.heinz.deviceai.continuumlocal` app.
- **Actual candidate main-app lineage:** research PR #209 HEAD `2b678da95a076ffc8dcf16a624eddc66934abbbd`, based on #205, #204, #203 and predecessor app work. Its Gradle target supports standalone `at.heinz.deviceai.continuumlocal`, while its local Fold7 release script uses `-PcontinuumLocalStandalone=true`. PR #209 fixed a wrong historical release gate.
- **Coding bridge source PR #211** HEAD `b885ff8a23bc7245f1a656648e94a100df639f5a`, stacked on #210, was originally based on old `main`. A history merge would import an unrelated legacy app configuration; that is **not done**.
- **Import/transfer branch #208** (and #206/#207) diverges from #209 at ancestor `f39fbe31a677d8c5020df11805b901abc4cedb73`. It has different Android and model-catalog changes. No blind merge. Reconcile individually only after tests and owner review.

## What this first integration commit actually changes

A new branch based on **exact PR #209 head** imported only six new, previously missing, optional coding bridge files from PR #211. These Git blobs were reused directly through the Git Trees API, without modifying their contents or merging PR #211's legacy main history. Four source/test scripts and two docs were transferred. They are dormant and not connected to production CCC1, Android UI, app model runtime, background jobs, owner data or secrets.

A new independent `scripts/continuum-integration-source-gate.mjs` performs read-only exact source checks on an owner-reviewed checkout:
- exact Git blob hashes of the four coding bridge scripts/tests;
- correct Gradle conditional `continuumLocalStandalone` package ID;
- standalone release script bound to that app ID;
- repaired owner-release gate, rejecting legacy Mentor Pair builder;
- LiteRT-LM source catalog remains present, no replacement GGUF backend;
- refusal of symlinked source paths or altered bridge bytes.

It only reports source identity. Its output explicitly marks `modelActuallyRunning`, `fullAndroidRegressionVerified`, `fold7DeviceVerified` and `apkInstalled` as false. Source text checking alone does not prove a build, signing identity, model compatibility, active research or intelligence improvement. Importing the bridge introduces no auto-login, paid fallback or key capture.

## Executed tests and evidence

Locally on Node.js 22.16.0:
```sh
node --check scripts/continuum-integration-source-gate.mjs
node --check scripts/continuum-integration-source-gate.test.mjs
node --test scripts/continuum-integration-source-gate.test.mjs scripts/continuum-coding-bridge.test.mjs scripts/continuum-selfhosted-coding.test.mjs
```

Result **30/30 PASS**, `0 FAIL`, `0 SKIP`. The 22 inherited bridge tests include genuine local file reads, a real 2/3 failing Node test, an exact allowlisted one-line patch verified on disposable synthetic sources (3/3 pass), actual local-loopback HTTP exchange with a **mocked model**, and negative network/model/cost cases. The eight new tests validate source identity, legacy Gradle rejection, release route, byte drift, symlinks, original LiteRT format and CLI refusal. All six tested files have been read back from GitHub with exact same Git blob SHAs as the local sources. No Android SDK/Gradle build or genuine local/cloud language-model generation was performed.

## Specific Gemma 4 findings – observed source, not an asserted root cause

`AgentActivity.kt` on PR #209 **already** restores pending model ID, version and SHA-256 from `onSaveInstanceState` after Android activity recreation; do not duplicate that feature. The real import passes a SAF stream into `LocalModelCatalog.import`, which copies/hash-checks the multi-gigabyte `.litertlm` then calls the native `inspector.inspect`. A displayed 120-second lifecycle timeout does not establish that either stage has failed. `AgentActivity.onDestroy` calls `localAgentExecutor.shutdownNow()`, while the same executor runs the model import; that is a concrete interruption risk if the Activity is destroyed during an import, **not proof of the Fold7 failure**. The exact code and lifecycle should be covered with an Android/Termux reproduction and actionable phase/byte/time evidence before redesigning the importer. Existing model bytes must remain untouched.

## Next required gates, in this order

1. On the actual clean `continuumlocal` candidate checkout, run `node scripts/continuum-integration-source-gate.mjs .` and ensure it passes. The static source gate and Node tests are separate from the main Gradle regression.
2. Run `scripts/continuum-fusion-full-regression.sh` against the exact commit SHA with existing JDK17, SDK36, Gradle8.11.1 and native AAPT2. Do not count historic targeted JUnit success as full-current regression.
3. On Fold7, reproduce original Gemma 4 E4B import/lifecycle with non-sensitive phases: SAF stream opened, copy byte count/time, completed SHA, native inspect, load, first genuine response, cold reopen. Do not send the model file or auth/owner data to a cloud service.
4. Compare SWB1/CRE1 training checkpoints with independent holdouts and respect the old installed app, archive and owner research switches. Do not overwrite/reject useful source data without review.
5. Only after a genuine local model proves a bounded coding task may existing CCC1 review receive a proposal in isolated workspace. No automatic patch, main push or signing.
6. A final owner-approved, original-P12-signed `continuumlocal` APK (next versionCode 27+) is allowed only after full clean release gates and Fold7 review. No APK or owner installation was performed here.

## Rollback

Close the new Draft PR without merging. Existing PRs #203–#211, default `main`, Android package installations, original signing P12, local owners' keys and model files remain untouched.
