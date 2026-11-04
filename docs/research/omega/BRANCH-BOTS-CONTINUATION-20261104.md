# Branch integration bots: continuation

Status: WIP / current-source integration in progress. This continuation starts from PR #515 commit `572e12ecc4ce5f7c79f2634a5b5773047b6d674f`. The prior 138-test result belongs to that verified checkpoint and is not a claim about unfinished changes.

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

The tests execute the actual shell entry point in disposable Git repositories and intercept its first download with a local failing canary. No network download, Gradle build, service deployment, APK creation or signing was performed. Exact red/green logs are under `docs/evidence/branch-bots-continuation-20261104/`. Independent integration review and the final combined test run remain pending at this checkpoint.
