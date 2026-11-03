# SOL 6.1 — P0 execution and repair ledger, 2026-11-03

Authoritative base: PR364 `1824f7adf4f2fe72fd52a7b53920e211b9118c2e` and its complete Master Handoff. PR364/362 handoff and review comments, Issue41 and architecture contracts were read. Work remains on the existing official repair branch; no main merge, owner data changes, signing, installation or publication.

## Proven defects and changes

- Shizuku: permission granted after the20s watchdog was discarded. A callback also bound without verifying actual permission. Two actual Robolectric regressions failed on the base bridge, then passed after checking the real grant and current server state independently of the pending flag.
- The connection dialog still dismissed on connect/manager actions and hid later status. It now remains visible, receives bridge status updates, and handles a missing manager. Two native Android widget tests pass; no device Binder result is claimed.
- Ready local Gemma was bypassed for anchored greetings/capability questions. The original request now reaches the existing runLocal path when READY and model identity exist, preserving the base Neuron-tutor exclusion. Native storage remains a Shizuku-independent probe. Navigation selection now follows displayed conversation results.
- A model answer beyond the existing8000-character agent boundary now fails with a distinct actionable diagnostic. It is never clipped or allowed to execute a proposal. Boundary and actual orchestrator regressions ran RED→GREEN on the host.
- PR362 screen/tool admission fix is imported only for LocalToolContinuation, its14 tests and the corresponding Activity call. Full JSON-encoded evidence is checked and unused native tool-response encoding is skipped for fresh screen turns. Exact source is pinned to `df25b8f360c321054c3020949a219f90041f0d55`. The unrelated code37 signing scripts are not imported; their unpinned-JDK review issue is not waived.
- Inherited memory stopwords, tutor routing, failed-engine ownership, Binder retry/timeouts, editor foreground fix and offline direct-test button are retained.

## Verification currently established

The latest targeted Gradle run compiled the integrated Android source using real SDK36/JDK17/Gradle8.11.1 and ran tests on JDK21:13 Shizuku lifecycle tests,2 connection-dialog tests,1 scrolling-editor test, all passed with zero skips. The two new late-grant/permission-authority checks were observed failing first. Source routing/closure Node checks:8 passed. These are host framework/admission checks; actual Shizuku server and native inference were not run.

An earlier complete Android/JVM+UI/lint run passed before reconciliation with1824. Its logs remain historical and cannot approve the reconciled source. Full exact-commit P0 runner, complete Node profile, separate JDK21 native-type tests and independent review are the next gate.

The private GitHub connector restored all executable/build/test inputs and required frozen fixtures. All613 base Git tree objects and the actual1824 commit object were independently reconstructed and verified by their Git object hashes. Sparse omissions are historical documentation/evidence only; no executable source or required fixture is replaced. The frozen1095-file v25 subtree is exactly `87e4c52821c4d5b5c61d4bab912b1dbe2aec1c25`. No token, secret or owner key was copied.

## Phase status and external boundaries

- P0: SOURCE_ONLY until a fresh matching acceptance receipt passes. The final receipt and raw logs are published on an `evidence/continuum-p0-<source-prefix>-20261103` branch in this same official repository, linked from PR364. `docs/evidence/sol61-p0-20261103/acceptance.json` must identify the exact unchanged PR364 source HEAD. Missing, mismatched or failed evidence is not acceptance. This avoids changing the tested source commit merely to embed its own future test results.
- P1: DEVICE_NOT_RUN. ADB device list is empty and this workspace has no Fold7 VPN/device connection. The live existing catalog was independently read: approved code38/0.3.37, original package and pinned signer. No new release is approved by this observation.
- P2–P6: not passed. No Fold7 native Gemma inference, on-device model-driven CCC1 RED→GREEN, worker endurance or trained quality improvement is claimed. These phases remain subject to the Master Handoff sequence.
- Release: original P12 remains on the existing trusted publisher. A higher code39+ candidate still needs full QA, actual Fold7 acceptance and release approval. Code38 scripts have not been represented as code39-compatible.

Rollback: retain the draft, revert only reviewed source changes with a new commit; do not reset shared history or uninstall the app. Next execution starts from the real remote HEAD, runs `scripts/continuum-fold7-repair-qa.sh` with its exact SHA and preserves every RED/GREEN log.

## Independent review and one repair pass

Independent read-only review of `1824..8c46e3f` identified one Important gap: READY-model greeting/capability inference could still propose a web tool under ALWAYS_ON. The canonical `NetworkTurnConstraint` now recognizes the existing bounded owner-local intents. It is used both by runLocal's prompt/approval upper bound and by the actual tool runner with the original owner turn. A fake-model behavioral regression exercises the real orchestrator and ToolGate with authorized ALWAYS_ON and a rewritten web query: the original local requests reach zero network delegate calls; a public-topic positive control still reaches one. These are policy fixtures, not actual model or internet evidence.

The same review's two user-visible diagnostic issues were also addressed under the owner's total-repair instruction: a real late permission denial now replaces a watchdog timeout, and oversized-model output shows the existing shorter-answer guidance in the conversation. The actual permission state remains authoritative. No new model/memory/worker subsystem was introduced.

RED:22 focused Android/JVM cases,2 failed (local-intent web execution and late denial); the conversation/source wiring and old teardown literal also failed in Node. GREEN:22 Android/JVM cases,0 failed/0 skipped, plus30 Node integration cases. The old Node teardown check now verifies stop→resetBinding→shell/connection invalidation; native bridge lifecycle behavior remains independently exercised, with no assertion disabled.

The independent reviewer did not claim Fold7, JNI generation/cancellation, real Shizuku service, thermal/memory quality, signing, installation or CCC1 autonomy. Those remain explicit device/release gates. Full exact-source QA follows this source checkpoint; historical targeted or pre-reconciliation results do not substitute for it.
