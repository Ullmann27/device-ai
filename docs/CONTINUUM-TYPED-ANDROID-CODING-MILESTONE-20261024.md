# Continuum Typed Android Coding Bridge – Development milestone

Date: 2026-10-24

Source repo: `Ullmann27/device-ai`. Builds on Sol draft PR #125 at `c46ad26bbb7982f8c602ac8fc528658f56f3747e` and safe rejection feedback draft PR #127 at `364c6aea766e395bc7026cc6fca7b24f7e5bf45b`.

## Milestone implemented

**The previously disconnected general Android coding-task draft is now wired to the existing local Termux callback, and the stored typed task reaches the existing CCC1 orchestrator.** It is *not* an APK release and is *not* proof of Fold7 execution. Two distinct, owner-confirmed stages exist:

1. In the main app, complete the explicitly bounded `ContinuumCodingTask` draft. The revision and SHA-256 are bound to the owner confirmation. Edits invalidate the confirmation. No cross-package Preview is used.
2. The existing internal `ContinuumMobileBuilderActivity` accepts only a strictly parsed canonical task + matching SHA-256. The owner separately requests **prepare**, which invokes the fixed `scripts/continuum-typed-coding-bridge.mjs prepare <base64url-canonical-json> <task-hash>`. The Node endpoint invokes existing `prepareCodingTask` and reserves a CCC1 journal/grant with no model execution.
3. Only after a validated `PREPARED_NOT_EXECUTED` callback has been stored (matched by request/operation, task ID and hash) is a second **run** enabled. The owner must confirm it. It invokes the same script `run <id> <hash>`, and the script calls existing `runCodingTask` using the frozen stored task grant. No dynamic shell commands, unbounded file grants, Git merge, install, signing or neural weight promotion are added.
4. Results return via the existing one-shot non-exported Termux callback receiver. Callback identities, output size, exact task fingerprint, fixed schema, conservative success gates and `approvedForMerge=false`, `installed=false`, `deviceVerified=false` are enforced before persisting a safe summary. No raw model output, arbitrary stderr, generated code or secrets enter the app's receipt store.

The original small `code-test` route and older CCC1/Termux operations remain intact.

## Exact changes

- Android app: `AgentActivity.kt`, `ContinuumMobileBuilderActivity.kt`, `ContinuumMobileBuildResultReceiver.kt`.
- Typed binding and validators: `ContinuumTypedCodingBridge.kt`; tests `ContinuumTypedCodingBridgeTest.kt`.
- Fixed Node local bridge: `scripts/continuum-typed-coding-bridge.mjs`; tests `scripts/continuum-typed-coding-bridge.test.mjs`.
- Android-to-Termux static regression guard: `.github/continuum/typed-android-coding-wiring.test.mjs`.

## Independent quality evidence

**Android:** Render service `srv-dbehb5gm7kps73dt3lsg`, deploy `dep-dbehdubncjis73e8s6qg`, checkout commit `7767a83176f90d82870810c527d5bb4c0d269aeb` (**the latest Android app/Kotlin production source**). Real Gradle `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` succeeded, `BUILD SUCCESSFUL in 5m 24s`, `57 actionable tasks: 57 executed`, `CONTINUUM_QA_EXIT=0`. Local Node preparation/rejection/scoped repair test gate also passed. QA explicitly did not publish or distribute a Preview APK.

**Node bridge and wiring:** Render service `srv-dbeh8doae00c73d5542g`, deploy `dep-dbehj8avcj2c73cvt1o0`, commit `7b99528317cfd95e274edd207403bfa1918aaed3`. **14 tests passed, 0 failed**, including actual `prepareCodingTask` and `runCodingTask` invoked against a temporary Git fixture and the full fixed Android/Termux callback wiring check.

**Important abstention:** The real CCC1 fixture on the Render host returned `ABSTAINED` and a `FAILED_REPAIR` receipt; the Android bridge correctly exposed `EXECUTION_REPORTED_UNVERIFIED` with no patch hash. This does NOT establish successful local model coding or a sandbox on Fold7. Model-based successful isolated repair had separately been demonstrated by Sol on its own host in PR #125, but the frozen repeat failed; its existing evidence remains unchanged.

**Previous full Node suite:** The historical release source-inventory test cannot find an older immutable Git object in Render's shallow static checkout; this is an infrastructure visibility restriction. It must never be disabled or reported as green. The focused integration suite and Android suite above are real green gates.

## Remaining blockers before owner release

- Test on the real Fold7, with the same signed Continuum package, configured Termux bridge, repo checkout, local model and isolation/capability checks. Verify both callback paths, lifecycle resume and 30-minute execution timeout.
- Validate real model+Docker/Termux-safe isolation against a new unseen task. When the executor cannot certify safety, `ABSTAINED` is required and must remain so.
- Add owner-visible cancellation/status and strong restart-replay proof for dropped callbacks, preserving existing CCC1 journal.
- Prove independently successful Android Kotlin patch or multi-file source change, not merely a host stub response.
- Repeat exact source/owner signature gates and owner release approval for a **new versionCode**; the current signed 0.3.21, package `at.heinz.deviceai.mentorpaired`, is NOT changed by this PR. No additional app may be released.

Status: `IMPLEMENTED_NOT_DEVICE_VERIFIED`. Main/production branch, owner signing, and productive installed application have not been changed.
