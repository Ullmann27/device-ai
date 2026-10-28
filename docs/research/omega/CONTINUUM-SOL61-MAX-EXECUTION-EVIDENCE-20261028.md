# Continuum / Sol 6.1 Max – execution evidence, 28.10.2026

Runtime: isolated Linux x86_64; Node.js v22.16.0. This was **not** an Android/Fold7 execution, not Android-Gradle/JUnit, not a Gemma inference and not an APK build.

## Commands actually executed

- Syntax: node --check scripts/continuum-sol-max-readiness.mjs
- Syntax: node --check scripts/continuum-sol-max-readiness.test.mjs
- New unit tests: node --test scripts/continuum-sol-max-readiness.test.mjs
- Combined regression: node --test scripts/continuum-coding-bridge.test.mjs scripts/continuum-selfhosted-coding.test.mjs scripts/continuum-integration-source-gate.test.mjs scripts/continuum-sol-max-readiness.test.mjs

Actual local results: **new 15/15 PASS**; combined 45/45 PASS, 0 fail, 0 skipped. The existing 30 checks were 22 bridge and 8 integration tests; counts are not additional to the 45. The original test run had 14/15 because the last test incorrectly expected an exception for a valid but mismatching SHA; that test expectation was corrected, without editing the classifier. The initial failure and the successful test TAP output are retained in the owner's linked local ZIP evidence.

New source files' Git blobs, read back from the GitHub branch and compared with locally executed files:

- scripts/continuum-sol-max-readiness.mjs — 3bf31bc6da7d9a25e6782d8d3ead4a8c271cdd05
- scripts/continuum-sol-max-readiness.test.mjs — 7d790f1b1223e50bc8e089058b1a3044ebdbb737

## Five completed source-check milestones

1. **Correct app package/release provenance:** distinguishes at.heinz.deviceai.continuumlocal from historical at.heinz.deviceai and mentorpaired. Source checks for Gradle, exact owner SHA gate and Standalone release path.
2. **Gemma import evidence protocol:** public/owner-reported phases SAF_OPENED, COPY_COMPLETED, SHA256_VERIFIED, INSPECT_OK, RUNTIME_READY, FIRST_NATIVE_RESPONSE, COLD_REOPEN_READY; validates monotonic duration, 64GiB byte limit, expected model SHA. Refuses false native inference claims based on external observations alone.
3. **SWB1/CRE1 and archive continuity:** enforces distinct old/new package identities, bounded integer counters and optional checkpoint SHA. Even matching hashes and externally claimed holdout success cannot authorize owner data transfer; SWB1 counters are never conflated with Gemma training.
4. **Local/Free-only coding bridge admission:** rejects paid unsuffixed free-model aliases, arbitrary file paths and any reported code/checkout modifications. Synthetic test success is not permission for private Git or actual model serving.
5. **Read-only CLI/owner release honesty:** classifies release blockers and exits 69 if the required Standalone QA proof is missing; rejects symlinks and sanitizes error output, does not create APKs/keys or change checkout.

These are completed *classification and validation* milestones, not the resolution of their underlying Android/model blockers.

## Real source blocker observed on GitHub

The actual current full regression script uses \`-PdeviceAiMentorPreview=true\` and has no independent \`-PcontinuumLocalStandaloneQa=true\` mode. Current Gradle defines \`continuumLocalStandalone\` and requires real signing credentials for that build variant. A green Mentor Preview unit test cannot establish a build/test of the exact new app package. The read-only gate must report \`LEGACY_MENTOR_PREVIEW_TEST_VARIANT\` and \`STANDALONE_UNSIGNED_QA_VARIANT_NOT_VERIFIED\` until corrected and independently tested.

Original hash and bytes of model were reported by the owner historically; no actual model byte streams or signer files read here. No passwords, API keys, OAuth data or P12 material in GitHub or test logs. No main branch, private owner data, app installation, signing, paid API, model downloads, or merge.

See docs/research/omega/CONTINUUM-SOL61-MAX-HANDOFF-20261028.md for complete next engineering tasks and issue register.
