<!-- coderabbit-ai-generated:v1 -->

# Mission OS B — window identity and integration handoff

Owner order: [PR 354 comment 6576751507](https://github.com/Ullmann27/device-ai/pull/354#issuecomment-6576751507). Base: `6fc252ecc05d49508023efe2835a983c14299074`, itself based on `41e1897b9099b911286d312a53c8c19307a49484`. The executable change in this checkpoint is scoped to window-event identity. The product click remains **NOT_REACHABLE**.

## Implemented and tested on host

`ContinuumScreenObservationService` now binds target content events to the actual target window ID during click verification. Missing IDs, an event from another window of the same package, a different sender, or an invalid window-change type cannot confirm a click. Window-state and window-set events require the active root's package and window ID to match before they can create a new foreground observation; an ambiguous event invalidates prior foreground evidence. Valid target window navigation remains accepted. There are no automatic click retries.

The prior Gitar findings on unrelated window and content events are addressed in `6fc252ec`, with their own RED/GREEN evidence under `docs/evidence/continuum-event-scope-20261102/`. The additional regressions here exposed incorrect postcondition/foreground acceptance after that commit. These are host tests using synthetic Robolectric framework windows; they are not Fold7 results.

## Original outcomes

- `mission-b-window-id-red.log`: full Android unit run exit 1, 1705 cases, two failures and three skips. The two deliberately failing cases demonstrated that a same-package different window and an unknown `TYPE_WINDOWS_CHANGED` event could incorrectly confirm an action. This RED was run with the uncommitted integration prototype, but its event handler was the pre-fix handler from `6fc252ec`.
- `mission-b-state-window-red.log`: targeted exit 1, one failure: a same-package `TYPE_WINDOW_STATE_CHANGED` from a different window could confirm a click. `mission-b-state-window-green.log`: same targeted test exit 0, one pass.
- `mission-b-foreground-red.log`: targeted exit 1, one failure: a mismatched window event produced a foreground observation. Final suite includes matching-window positive, mismatched-window negative, and unknown-sender negative controls.
- Final Android and Node counts, exact commands, exit codes, source identities and evidence hashes are recorded in `results.json`. Three Gradle and 13 Node skips remain unverified.

The targeted commands use the supplied Gradle init scripts to select a test, because the repository's disposable QA guard rejects `--tests` as an additional task name. The init scripts select tests only and do not modify repository sources or signing configuration. The SDK compile-only LiteRT stub and Robolectric shadows do not establish actual model inference, Android device behavior or APK compatibility.

## Integration prototype — not delivered as executable code

`prototype-not-for-application.patch` is the exact experimental patch based on `6fc252ec`, archived for review and continuation. It predates the final window fixes and is **not an application patch**: do not deploy or apply it without reconciling it with the current source and resolving the issues below. A synthetic prototype run passed 1702 Android tests, with three skips, and demonstrated Gate-to-`ACTION_CLICK` with an observed postcondition using Robolectric shadows. It did not verify actual Activity consent, the Fold7, or durable uncertainty. The archived patch is evidence rather than an enabled feature.

The proposal uses a bounded one-use screen basis, an owner-authored Settings battery task, ToolGate consent, a fresh observation after restoring Settings, and a single native click. Do not add `click_visible_node` to `ScreenObservationPrompt.allowsTool`: screen text remains untrusted data.

## Blocking work for the next agent

1. Reconcile the prototype with the current event checks. Design and test a backwards-compatible, durable receipt transition that records **may have dispatched** before `ACTION_CLICK`; after a crash, timeout, revoked permission or absent postcondition, report **UNKNOWN**, never successful and never automatically replay. Existing receipt `STARTED` and generic `FAILED` cannot distinguish a pre-dispatch rejection from a dispatched-but-unconfirmed action. Preserve existing receipt records and original failures.
2. Prove the actual Activity lifecycle: an owner must be able to return to the confirmation UI while Settings is foreground; do not rely on an unapproved background activity launch. Restore Settings only as explicitly displayed and confirmed, freshly observe it, compare the exact previously approved target/window, and require new consent if changed. Bind the approval to original owner task, call ID, session, grant epoch, revision and expiry. Keep screenshots and other app data out of persistent owner knowledge.
3. Repeat RED/GREEN tests for confirmation, session/cancellation, wrong package/window, changed screen, overlays, duplicate labels, passwords, permission revocation, timeout and restart/replay. Run full Android unit tests, Kotlin compilation, lint, critical Node and full Node; obtain a fresh independent review of the final SHA.
4. **DEVICE_VERIFIED: NOT_RUN.** No device was listed by the configured SDK's `adb devices -l`; no APK was assembled or signed. For actual Fold7 proof, the owner must provide an authorized device session and an owner-signed compatible app build, enable Accessibility and confirm each real action. Observe Settings → Battery → the newly displayed screen, then read only the actual visible result. Record the device model/Android version, timestamps and outcomes without retaining private screen data.

No main merge, signing change, owner-data modification, model-weight change, Shizuku requirement or APK release is part of this checkpoint.
