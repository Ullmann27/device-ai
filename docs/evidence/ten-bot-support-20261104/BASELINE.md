# Support-team baseline

Date: 2026-11-04. Production/test source: PR #483 at
`578bdfd12f896cc990bf1a043bc1b248ba3adb6d`; the initial support commit changes
documentation only. File hashes are recorded in `baseline-source-sha256.txt`.

## Executed results

| Gate | Actual result | Scope |
| --- | --- | --- |
| Worker/process/service/update Node checks | 45 PASS, 0 FAIL, 0 SKIP, exit 0 | Local Node v24.22.1; real synthetic worker subprocesses plus contract tests |
| Signed-update policy JUnit | 8 PASS, 0 FAIL, exit 0 | Production pure Kotlin policy compiled with local Kotlin/JDK; no Android device |

Node command:

```sh
node --test \
  .github/continuum/local-development-worker.test.mjs \
  .github/continuum/local-worker-process.test.mjs \
  .github/continuum/coding-service.test.mjs \
  scripts/continuum-one-tap-updater-contract.test.mjs \
  scripts/continuum-owner-release-stage.test.mjs
```

The policy gate compiles `ContinuumSignedUpdatePolicy.kt` and its existing
`ContinuumSignedUpdatePolicyTest.kt` with the installed Kotlin compiler and
JUnit 4.13.2/Hamcrest 1.3, then runs
`org.junit.runner.JUnitCore at.heinz.deviceai.intelligence.ContinuumSignedUpdatePolicyTest`.
This confirms the pure policy, including old/equal versions, wrong package or
signer, unapproved metadata and a mismatched download host/hash. It does not
download, publish, sign or install an APK.

Raw results: `baseline-worker-update.log`, `updater-policy-compile.log` and
`updater-policy-junit.log`. Gradle, APK, physical Fold7, native model execution
and visual acceptance for this support branch remain NOT_RUN. No S01 replay or
external service was invoked. These existing green baselines do not establish
that currently untested support changes or other workstreams pass.
