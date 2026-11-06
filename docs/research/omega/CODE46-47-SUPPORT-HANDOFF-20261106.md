# Code46 / Code47 support handoff — 2026-11-06

Status: CODE46_DELIVERED_AND_INDEPENDENTLY_VERIFIED / CODE47_ACTIVE.

## Ownership

The owner requested exactly three additional helpers for Code46 repair and APK building, then implementation, improvement, repair and APK building on Code47 after Code46 finishes. Existing PR #575 and PR #576 remain authoritative. This support branch does not overwrite their active branches or worktrees. No nested teams; one heavy local compiler at a time.

## Code46 delivered

- Frozen source: `577f0931e7937ffcac02dc855a540f093facdb3b`; code46 / 0.3.45.
- Existing original publisher: `srv-dbf1tr2vcj2c73ehpfog`; deploy `dep-dbn1htjncjis73c9vlng`.
- Local sequential Android result independently recounted: 626 XML suites, 3559 total, 3539 passed, 20 skipped, zero failures/errors. Original primary team corrected the five RectF harness failures; support did not duplicate that repair.
- Render gates: Android required tests GREEN 18:55:50Z; PRESIGN_QA_PASS 18:58:52Z; signed staging 19:00:52Z; LIVE 2026-11-06T19:00:55.5398Z.
- Independent actual APK verification at 2026-11-06T19:05:55Z: 101876503 bytes; version46 / 0.3.45; original package; exactly one original signer; APK Signature Scheme v2 valid. Hash checked before and after apksigner/aapt2.
- APK SHA256: `0930614bbcc8e53d75283dfe47a53a73e758430a99af3698f31b88446babc759`.
- Fresh live catalog matches downloaded catalog bytes. Android catalog contract passes against exact Code46 source. BUILD-INFO has exactly six unique matching keys. A separate helper independently checked both public endpoints and rejected duplicate keys.
- Root reused the primary workspace's completed HTTPS download and independently checked its bytes. No duplicate APK download or publisher job was started.
- Raw verification: `docs/evidence/code46-47-support-20261106/code46-release/`.
- Download delivered to Heinz: https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/Continuum-Lokal-0930614bbcc8e53d75283dfe47a53a73e758430a99af3698f31b88446babc759.apk

## Code47 real baseline SDK result

Support independently materialized and Git-blob-verified source `f26d1063b25c65c22b8d411789d1733c92e3ad72`. Its production/test source is unchanged in documentation checkpoint `6bd0e172b5615854e021e43aa0ed04a1978eb733`.

The real SDK/Gradle run completed in 36m14s, exit1. Production and test compilation passed. 628 XML suites: 3577 total, 3556 passed, one failed, 20 skipped. Lint was not reached. Exact failed case: `LocalPromptSizeAndStabilityTest.timeAnchorIsInTheRequiredTailNotInTheStaticPrefix`; assertion: time anchor must be part of requiredTail.

Source review identified an outdated structural assertion after M1 changed direct framing to `LocalClockIntent.promptAnchor -> promptEnd -> LocalDecisionPromptFrame(requiredTail=promptEnd) -> LocalPromptFrame`. The production time-anchor path is present. A targeted test correction must still check the true Activity connection and exclude the clock from the static prefix; it must not merely search for a clock call somewhere.

Raw baseline receipt, failed XML and full Gradle log: `docs/evidence/code46-47-support-20261106/code47-baseline/`. This unmodified f26d result does not cover subsequent M2 changes or either support fix.

Command used offline, with isolated checkout/cache, existing JDK17/Android SDK and one worker:

```sh
gradle --offline --no-daemon --max-workers=1 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumUiWidgetTests=true \
  -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=47 \
  :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

An earlier filtered attempt was rejected during configuration by the existing strict task-name guard. No product assertion ran in that attempt; the guard was not weakened.

## Code47 conflict correction

Support commit `a6b29908ecc10bc9279aeaa5751e42683baa23d5` changes two production lines so all relevant active decisions participate in conflict detection before secret/length display filtering. Two regressions use actual OwnerStatement -> WorkDecisionRecall.items.

Author probe: RED2/2 -> GREEN2/2. Root used the same two compiled JUnit test classes containing 19 tests: original SDK digest 17 PASS /2 expected FAIL; corrected digest 19 PASS /0 FAIL. Independent review found no blocker. Exact commands, hashes, patch and raw evidence are in the parent evidence directory.

Latest inspected primary PR #576 source `9ee7535bb131504cc85419afd30f446089a40171` has M0/M1/M2 integrated but does not contain this correction. Its M2 smoke evidence uses substitutes; actual full SDK/JUnit validation remains required. Original relevant tests are preserved. The fix remains semantically compatible. Mergeability metadata was stale/inconsistent; no overlapping changed paths or actual merge conflict was established.

## Active continuation

The same three helpers were reassigned after verified Code46 delivery:

1. Isolated M2 start-boundary correction on exact 9ee7535: the new journal start currently observes the pre-dispatch callback before a possible STOP/deadline rejection or failed durable dispatch commitment. Final receipts do not falsely claim success, but the transient EXECUTING state can precede any backend call. Reproduce then repair with a separate committed-dispatch observer; preserve the existing pre-dispatch callback and STOP contract.
2. Correct the outdated time-anchor source contract in an independent checkout, retaining meaningful negative cases and real wrapper behavior.
3. Independently review both corrections and the existing conflict fix.

Before final Code47 delivery: integrate reviewed source changes on the current primary candidate, freeze its exact SHA, run full required Node/backend/Android/UI/lint/APK gates, then use the existing original publisher and verify actual bytes/signature/version/catalog again. No Code47 APK or final-source PASS is currently claimed.

Original package: `at.heinz.deviceai.continuumlocal`.
Original certificate: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.

No main merge, paid services, GitHub Actions, user-data deletion or signing-key retrieval. Original head/orb, offline policy and foreign worktrees remain protected. Physical Fold7 installation, actual LiteRT answers, audio and GPU acceptance remain NOT_RUN.

## Rollback and noninterference

- Code46 remains the verified owner release at source `577f0931e7937ffcac02dc855a540f093facdb3b`; do not rebuild or redeploy it for a Code47 fault. The original publisher is unchanged.
- Code47's pre-support base is `6bd0e172b5615854e021e43aa0ed04a1978eb733`; the primary later advanced independently to `9ee7535bb131504cc85419afd30f446089a40171`. Revert the isolated support patches from PR #577 if any regressions surface; preserve primary M0/M1/M2 and saved work. No persistence migration is part of the decision-conflict or test-contract corrections.
- The time-anchor source test update in support commit `492f1ad2bd5091351c1090080663d6f4201d905f` checks `promptEnd` flowing via `LocalDecisionPromptFrame(requiredTail=promptEnd)` to both underlying `LocalPromptFrame` branches. It has a positive and three deliberately broken negative source probes. This is structural verification only: full Gradle/JUnit on the final integrated source remains pending.
- Never force-update primary integration branches or the publisher. If final-source Android/Node/lint/APK QA is RED, preserve the exact failure report, fix the isolated defect and rerun before any signing. No duplicate deploy jobs or new signing channel.
