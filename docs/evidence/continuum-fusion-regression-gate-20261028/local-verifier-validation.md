# Local verifier validation – 2026-10-28

**Executed in this chat's isolated x86_64 container, NOT in the original Android/Gradle owner workspace.**

Runtime: Node v22.16.0; GNU bash 5.2.37; system Java 21 (NOT the required Android JDK17). No `/workspace/device-ai-fusion`, Gradle 8.11.1 or Android SDK36 were available. No private repo checkout; no APK, model or device execution.

Source identity was verified **byte-for-byte with GitHub git-blob hashes**, by `git hash-object` of the exact local files and independently reading their GitHub `fetch_file().sha`:

- `scripts/continuum-fusion-full-regression.sh` = `d0f92a066d24e751728bffeaf3eff6392a9e9aa0`
- `scripts/continuum-fusion-regression-report.mjs` = `433bcda4610bfe186fd8a1559193aa5bb42a7663`
- `scripts/continuum-fusion-regression-report.test.mjs` = `4adf006deab97c24cfd4f87cc7d11e81e09b1046`

## Actual local test commands and results

```text
$ bash -n scripts/continuum-fusion-full-regression.sh
SHELL_SYNTAX_PASS
$ node --test scripts/continuum-fusion-regression-report.test.mjs
TAP version 13
1..6
# tests 6
# suites 0
# pass 6
# fail 0
# cancelled 0
# skipped 0
# todo 0
$ bash scripts/continuum-fusion-full-regression.sh aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
CONTINUUM_FULL_REGRESSION_BLOCKED: Full Android checkout required
EXIT_CODE=69
```

The six Node tests cover normal JUnit XML, truncated/missing/contradictory reports, failures/skips/missing files, nonzero Gradle/Node exits, stale results, and preservation of XML plus SHA-256 evidence outside the source tree.

**Android full `:app:testDebugUnitTest` regression NOT_RUN / UNAVAILABLE in this isolated environment.** No JUnit count is inferred from these six Node tests; the earlier 56 targeted JUnit passes belong to the parent PR #203. The fail-closed runner does not sign/install, download, start cloud CI, use owner secrets, query real memory, modify models, or promote rankings.

### Correct next command in the authentic, clean, local JDK17/SDK36/Gradle8.11.1 checkout

```bash
# On the actual checkout of this regression-gate branch, with JDK17 and SDK36:
export JAVA_HOME=/workspace/continuum-tools/jdk17
export ANDROID_HOME=/workspace/continuum-tools/android-sdk
export GRADLE_USER_HOME=/workspace/continuum-tools/gradle-cache
bash scripts/continuum-fusion-full-regression.sh "$(git rev-parse HEAD)"
```

The script checks an exact 40-digit SHA, clean tree, forbidden signing secrets, toolchain versions, native AAPT2 and Node LTS. It then runs the **unfiltered** `:app:testDebugUnitTest` with `:app:lintDebug` offline and uses `--rerun-tasks` to prevent cached filtered test results from being mistaken for new test execution. It also runs the existing Node contracts and the new verifier tests. Logs, fresh JUnit XML, SHA-256 manifest and `summary.json` are written outside the checkout under a private directory; PASS is allowed only if both Gradle and Node exit zero and fresh, complete JUnit reports contain no failures/errors/skips.

After the authentic run, compare the reported SHA and original logs before advancing to separately owner-approved Gemma and Fold7 measurements.