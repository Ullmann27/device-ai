# Code46 / Code47 support handoff — 2026-11-06

Status: CODE46_DELIVERED_AND_INDEPENDENTLY_VERIFIED / CODE47_REVIEWED_CORRECTIONS_AND_RUNTIME_WIRING_AUDIT.

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

## Completed support correction stream

The same three helpers were reassigned after verified Code46 delivery:

1. Isolated M2 start-boundary correction on exact 9ee7535: the new journal start currently observes the pre-dispatch callback before a possible STOP/deadline rejection or failed durable dispatch commitment. Final receipts do not falsely claim success, but the transient EXECUTING state can precede any backend call. Reproduce then repair with a separate committed-dispatch observer; preserve the existing pre-dispatch callback and STOP contract.
2. Correct the outdated time-anchor source contract in an independent checkout, retaining meaningful negative cases and real wrapper behavior.
3. Independently review both corrections and the existing conflict fix.

Before final Code47 delivery: integrate reviewed source changes on the current primary candidate, freeze its exact SHA, run full required Node/backend/Android/UI/lint/APK gates, then use the existing original publisher and verify actual bytes/signature/version/catalog again. No Code47 APK or final-source PASS is currently claimed.

Original package: `at.heinz.deviceai.continuumlocal`.
Original certificate: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.

No main merge, paid services, GitHub Actions, user-data deletion or signing-key retrieval. Original head/orb, offline policy and foreign worktrees remain protected. Physical Fold7 installation, actual LiteRT answers, audio and GPU acceptance remain NOT_RUN.

## Consolidated candidate after independent reviews

A new isolated branch `codex/continuum-code47-qa-candidate-20261106` combines the verified support history through ee9697caff3a4dd716f0e38e87d54132ae1710d6 with primary source9ee7535bb131504cc85419afd30f446089a40171. The nine primary paths and earlier support paths do not overlap. PR577's later concurrent automation commits remain preserved on their own branch; no rollback or overwrite occurred.

Three corrections are included: the earlier decision-conflict fix (19 targeted tests), committed-dispatch journal-start fix (RED7/4 failures, GREEN43/43), and restored time-anchor contract (RED8/1 failure, GREEN8/8; five defective source controls each rejected). These are separate focused runs and must not be summed as unique full-suite coverage. All three received independent diff/evidence review. Standard ToolGate callbacks/STOP semantics and the trusted-clock requirement remain intact. Raw new evidence is in `m2-dispatch/` and `time-anchor/` under the support evidence directory.

Full combined SDK/lint/APK verification remains NOT_RUN. The previous local source/toolchain workspaces disappeared during the ongoing session; verified source contents and raw evidence were recovered from retained tool results and are preserved in this commit. The published Code46 artifact and GitHub histories are unaffected. Re-materialize this exact candidate and a supported toolchain before claiming new execution.

## Owner's new device feedback and acceptance scope

After actually trying Code46, Heinz reports weak head movement, absent turn-away/workscreen animation, natural app-opening requests failing, inconsistent assistant self-description, context loss and interrupted/overlong answers. He specifically requires flexible phrasing with a configurable assistant name, app-independent intent handling, compound requests such as opening an app and preparing a message, and contextual follow-ups. These are acceptance scenarios; they are not authorization to send the example message to a real recipient.

The same three helpers audited the actual frozen Code46/Code47 call chains. Primary Avatar PR565 and PR574 are ancestors of Code46; the visual code is present but key input/presentation links are gated or missing. Before any Code47 release, address the concrete verified causes: opening intents diverted into search-only flow; CHAT hiding work windows; missing sustained tool-work gaze; incomplete turn/presentation-generation binding; bounded context/persistence integrity; inconsistent identity routing; and incompatible capture/output limits. An embedded live screen of an arbitrary external app is not implemented by the current status window and must not be claimed.

Audits are source-based. Actual Fold7/GPU/model measurements and the exact cause of each reported device event remain unverified. Existing permissions, cancellation, source completeness, negative-intent and tool-output boundaries must be preserved.

## Current active repair

The verified call-chain audit, three bounded repair branches and acceptance cases are now recorded in [Code47 live wiring audit](CODE47-LIVE-WIRING-AUDIT-20261106.md). PR578 is the isolated integration candidate. Natural app actions and visual work episodes are in PR579; context/identity repair is in PR580; output/capture repair is in its own named branch. The same three helpers are active. Only the visual helper writes AgentActivity; other helpers provide exact integration fragments. A single restored native build toolchain is shared for validation.

Current status remains REPAIRS_IN_PROGRESS / FINAL_QA_NOT_RUN. The transition automation is paused to avoid concurrent writes observed on PR577; active helper work is not presented as a permanent background service.
