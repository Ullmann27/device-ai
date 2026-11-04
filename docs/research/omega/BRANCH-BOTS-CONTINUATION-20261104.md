# Branch integration bots: continuation

Status: selected integration changes implemented and independently reviewed. The final combined targeted suite passed **183/183 tests, 0 failures, 0 skips**. This continuation starts from PR #515 commit `572e12ecc4ce5f7c79f2634a5b5773047b6d674f`. The prior 138-test result remains historical evidence for that checkpoint. APK and device acceptance remain NOT_RUN.

## Bounded tasks

1. Compare the current integration/repair branches against exact source identities and identify a small ready connection without duplicating another active team's work.
2. Verify the Android-first connection between branch-bot APK diagnosis and the existing `resolveTermuxEnvironment()` contract. If a missing connection is reproduced, reuse that resolver and preserve all report/source/queue gates.
3. Recheck available local or existing authorized Android QA capability. No new paid service, signing, installation, Actions run or unverified build claim.

Three development helpers cover source coordination, Android environment linkage and build-route diagnosis. Root integrates selected changes, runs relevant tests and obtains independent review. Existing BacklogStore, CCC1 and local scheduler remain authoritative; no automatic main merge or parallel worker system.

## Evidence boundary

The local continuation copy materializes selected exact files from the published PR, not a complete Android checkout or the original product history. Product source SHA and local snapshot SHA are kept separate. Final code, test results, source identities and remaining gates will be appended to the main handoff in this same PR.

## Checkpoint: existing QA gate connected

The two-file QA preflight correction from PR #505, pinned at `8b72db66e5338fbc3a791adc30254f4ca77007ad`, has been selected and reproduced against our original script. Its exact source blob is `9ea8eae2011504b5283a0cf4e62231f0aaa7c39b`; its test blob is `fe2278df7b543ceb2bd51f22188bd9e361805782`.

Before adoption, the 43 tests reproduced 14 failures and 29 passes. After the selective source change, all 43 passed with no skips, and `bash -n` passed. Both QA modes now validate canonical decimal version codes before setup/downloads and reject the previously omitted mentor P12 Base64 signing variable. This is an entry-gate correction, not an automatic version increment or a new release permission.

The tests execute the actual shell entry point in disposable Git repositories and intercept its first download with a local failing canary. No network download, Gradle build, service deployment, APK creation or signing was performed. Exact red/green logs are under `docs/evidence/branch-bots-continuation-20261104/`. Independent review reran all 43 tests successfully and found no concrete remaining product defect in the two-file scope.

## Android environment connection

Two additional regression tests first failed: a host probe did not receive its explicitly selected environment, and the Bot CLI could not resolve existing owner-home SDK/Gradle fixtures for a noninteractive Termux invocation. The minimal correction reuses `resolveTermuxEnvironment()` in the CLI and passes the existing inspector's `env` to its three existing Java/Javac/Gradle subprocesses. No new builder, wrapper or API was added; inputs and `process.env` remain unchanged.

The targeted suite passed 61/61, independently repeated with the same result. The final merged suite passed 183/183. Tests use actual subprocess fixtures and a clearly simulated ARM64 discriminator, not an Android device. Missing tools remain blocked, reports retain exact byte hashes, and proposed branch-review tasks do not acquire execution authority.

## Source coordination and current build boundary

Direct reads at exact commits established: #506 at `8abea828833d8833d40e3d3d36feaa6d3685b82e` lacks both QA corrections; #513 at `28b228698aef8c52e46c72b71fdd5e1915d6e76c` and #517 at `46e5fa6b09454ad6f6862e80478eb0f2d5545149` contain only the early version check. A later moving PR file-list result must not be assigned to those earlier commit IDs. See `independent-review.json` for the corrected, directly verified provenance and avoid duplicate work when combining later heads.

Fresh local diagnosis still reports missing explicit JDK, SDK36, Gradle8.11.1 and native AAPT2. The existing Render M8 QA service was observed on the older source `861d2faed694fc1869050cf5ed81a593701801f5`; its historical LIVE state does not certify this PR. No service was retargeted or deployed. Read `build-availability.json` for the actual boundary.

## Final handoff

Changed scope: six source/test files; five JavaScript syntax checks, one Bash syntax check and `git diff --check` passed. Exact file identities, command, raw test output and independent reviews are in the continuation evidence folder. The next APK integration must use a complete isolated checkout and actual appropriate toolchain, followed by the existing compilation/test/build/device gates. Rollback is to omit these continuation commits; original release identity, signing and main remain unchanged.
