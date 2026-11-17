# MΩ-1 Android integration handoff

Status: SOURCE_INTEGRATED in the isolated lane / CONTRACT_TEST verified, including 13 independent adversarial tests; independent scoped source review ACCEPT in [PR419](https://github.com/Ullmann27/device-ai/pull/419). Device and native inference NOT_RUN. Base `ca93fb4f7a17ff7a2a13debd93197ba485594f06`; final production/test freeze `037370f0544d3ed2ee7686082c41c78046daa64d`; isolated branch `codex/momega1-android-20261103`. EXP-00/01/02/03/05/07/08/11/12 apply. Chief explicitly allocated BuilderActivity, ResultReceiver, TypedCodingBridge and focused tests after this audit. AgentActivity, ToolGate, manifest and LiteRT remain unchanged.

## Baseline call path and missing transitions

- `AgentActivity.runLocal` (6467) captures loaded model and cancellation. Its existing bounded orchestrator (6522) calls the same LiteRT runtime and returns `AgentModelOutput` (6710), then ToolGate/owner confirmation (6720–6767). It has no typed coding intent output or caller of `draftCodingTask` from generated model data.
- The separate owner coding form builds `ContinuumCodingTask.Draft` (7189), validates via the development conversation, explicitly approves a reviewed revision/hash (7238), and opens the Builder with canonical JSON/hash (7250). This is an existing path to reuse, not a scheduler to replace.
- `ContinuumTypedCodingBridge.parseAuthorization` validates exact schema/canonical JSON/hash and strict file/call/context bounds. A hash is integrity, never owner identity. Builder receives untrusted extras: its current exported tool activity does not authenticate the sender, so its own complete review at 78–103 is essential; no new exported component is needed or authorized.
- Builder prepares through fixed Node argv (365), official Termux RUN_COMMAND (494), one-shot explicit mutable PendingIntent (488), private pending identity committed before dispatch (531). Receiver is non-exported; it binds callback URI/operation/request (46–73), rejects missing/truncated output and validates source/hash/status (118–141).
- Concrete gap: prepared state stores only ID/hash/commit (Receiver 134–137), and RUN confirmation displays only this abbreviated tuple (Builder 119–126). The full task cannot be reviewed after process recreation. RUN dispatch checks current ID/hash but rereads commit (376–390), and RUN leaves prepared state reusable. Minimal allocated patch: persist canonical JSON through prepare, reparse/rebind it for complete RUN review, bind the entire reviewed snapshot again at dispatch, consume prepared state when RUN is durably scheduled, and refuse legacy incomplete tuples. Existing pending slot/CCC1 journal remains canonical.

## Shared LiteRT feasibility

`LocalModelHost` is a singleton per Android process (19–29), storing catalog in app-private filesDir and cache in cacheDir. Termux has another UID/process; no existing authenticated IPC transports Node requests into this loaded engine. Import hashes actual bytes and `catalog.get` verifies the stored file; `LocalTurnModelBinding` checks ID/version/hash metadata per turn. This does not authenticate native runtime libraries or provide continuous byte hashing during inference.

LiteRT admits only READY under its lock, owns one active generation, suppresses cancelled callbacks, defers unload until terminal native completion, and retains resources after close failure. Sharing would require one serialized owner lease with bounded request/context/token/byte/time budgets, authenticated caller/session identity, nonce/replay ledger, revoke/STOP propagation and lifecycle recovery. A localhost listener or unauthenticated export does not establish these properties. ALPHA and DELTA agreed no small existing shared interface exists. No IPC implementation or second model was added here; ALPHA is investigating its explicitly approved hash-pinned local runtime path and must block absent weights/runtime/isolation.

## Validation and device limits

Initial tool discovery: Node and Java present; `adb` and `kotlinc` absent from PATH. No SDK/device installation or owner data access attempted. Fresh real inference/Fold7/latency/RAM/energy are UNAVAILABLE/NOT_RUN, never inherited from historical handoffs. Exact-source contract results will be added below.

Next: RED reproducer for missing full prepared review; minimal patch under allocation; focused host negatives, then independent Chief review. Proposed subsequent model connection: bounded typed task *candidate data* into the existing review form, owner supplies/checks missing source/scope/acceptance; approval invokes existing prepare/run. No model output can create Authorization or a shell command. AgentActivity wiring requires separate allocation.

Rollback: revert this isolated patch; do not migrate old prepared tuples into new authority, replay uncertain tools, touch signer/package/main or restore stale owner data.

## Implemented source and state transitions

The earlier paragraph describes the baseline proposal. The allocated patch is now implemented at the frozen source above:

- `ContinuumTypedCodingBridge.kt:60` reparses complete canonical task JSON and binds ID, hash and source commit. `:69` renders the complete execution review using the same lossless field formatter as preparation. All goal text, source paths, acceptance criteria, chain, call/context limits and full SHA remain visible.
- `ContinuumMobileBuildResultReceiver.kt:133` validates the complete pending task before accepting PREPARE or RUN results. A valid PREPARE stores canonical JSON plus the validated CCC1 journal ID (`:150`). Missing, changed, invalid or legacy incomplete saved data cannot enable RUN. Callback URI/request/operation/truncation/source/hash gates remain in place; a duplicate consumed callback cannot replace the current cancellation target.
- `ContinuumMobileBuilderActivity.kt:118` captures the complete task and its prepared journal ID for the explicit RUN dialog. `:405` rechecks both against the current private saved state when the owner clicks execution. A changed source, JSON, task hash or journal ID refuses dispatch.
- `:535` validates the full snapshot before durably saving pending execution. RUN restores its own prepared CCC1 journal ID to the existing canonical cancellation target (`:555`), then consumes all prepared keys in the same commit before invoking the fixed Termux command. Thus an intervening generic status for task B cannot redirect cancellation of typed task A. Failed or uncertain launch requires fresh review/preparation; no automatic replay occurs.
- `:321` registers a preference listener only while resumed and unregisters it in onPause. A foreground validated callback refreshes status/RUN availability without polling or automatic execution. The existing manual refresh button also remains available; the baseline was not limited to onResume refresh alone.
- The existing explicit, target-bound CCC1 abort dialog and separate ABORT slot remain the only new task's cancellation path. Chat STOP does not prove a separately dispatched Termux process stopped. Neither an abort request nor its UI text counts as a confirmed worker cancellation; the existing validated result must arrive.
- Independent review reproduced a missing revocation transition in `b95858c1`: a terminal abort left prepared execution consent available. Final `037370f0` consumes the matching prepared snapshot in the durable explicit abort dispatch commit, blocks RUN while its journal has an abort pending, and clears the matching prepared association on a valid target-bound abort callback. Local consent is revoked even if the remote worker stop remains uncertain. A stale/foreign abort or abort(A) cannot clear unrelated prepared task B. No independent finding is presented as proven model re-execution: the witnessed defect was Android RUN_COMMAND dispatch.

No second scheduler/model, new network listener, exported component, tool name, source/owner authorization bypass, merge, APK build, install or release was added.

## Exact-source evidence

Raw test logs, JUnit XML, exit codes, source-receipt digests and candidate file hashes are committed in `docs/evidence/momega1/android/`; see `manifest.json` and `README.md`.

| Frozen source | Actual check | Result |
| --- | --- | --- |
| `d43c043a` (production unchanged from base) | 7 real Robolectric widget/receiver tests | RED: 6 failures, 1 pass, 0 skips; stale commit dispatch and retained prepared state actually observed |
| `c10ab9d3` | 9 widget/receiver tests | 8 pass, 1 RED: fresh typed task lacked CCC1 cancellation target |
| `e2523431` | 6 focused JVM/UI classes | 30/30 pass, 0 skips; intermediate result only |
| `5c9c31c0` | 12 widget/receiver tests | 10 pass, 2 RED: intervening status retained wrong target B; changed prepared journal ID still dispatched RUN_COMMAND |
| `b95858c1` | 6 focused JVM/UI classes, real Gradle + JDK21 Test JVM | GREEN: 32/32 pass, 0 failures/errors/skips; before/after source receipts identical |
| `b95858c1` | Independent frozen 9-case Android suite | RED: 7 pass, 2 failures; confirmed cancellation retained prepared permission and an open dialog dispatched RUN_COMMAND |
| `037370f0` | 8 focused JVM/UI classes, including 13 independent cases | GREEN: 45/45 pass, 0 failures/errors/skips; source receipts identical and independent test hashes unchanged |
| `037370f0` | `node --test scripts/continuum-typed-coding-bridge.test.mjs` | 15/15 pass, 0 skips; includes actual synthetic CCC1/bridge contracts, no real model inference |
| `037370f0` | Adjacent mobile Builder/coding Node contracts (6 entries) | 65/65 pass, 0 skips; exact command in evidence manifest |

The earlier dual-entry Node command reported 19 because the script already imports the four wiring tests. The final single-entry result avoids duplicate counting. An initial Android attempt changed tests during compilation and was rejected as mixed-source. The first stable fixture also failed to drain Android dialog messages; dispatch/replay claims use the corrected `d43c043a` RED only. Both earlier attempts remain transparently retained in QA evidence; they are not acceptance substitutes.

These are CONTRACT_TEST results: Robolectric executes actual Activity/Receiver/widget code with a shadow Termux service boundary. They are not real Termux processes, a shared LiteRT inference proof, device power/RAM measurements, a full Android suite, lint, an APK or Fold7 acceptance. LOCAL_MODEL_HOST / FOLD7_MODEL / FOLD7_END_TO_END / RELEASE_READY remain NOT_RUN here. SDK/JDK/Gradle tools were provisioned by the QA lane, not by this lane; device availability was not inferred from their installation.

## Deferred model-to-task connection

There is still no actual `runLocal` model-output-to-typed-task adapter. `ToolRegistry` has no coding tool and `AgentModelOutput` carries only response/proposal. A minimally scoped follow-up would add an explicit coding-planning entry using the existing runLocal model/orchestrator and tool-free isolated thinking on the same loaded engine, with a bounded whole-JSON schema for candidate draft data only. Strict parsing must reject prose/extra fields, preserve missing source/scope/criteria as incomplete, and generate the task ID in the app. A successful, still-bound model/session turn may offer the existing owner form with that candidate prefilled. The owner then edits and reviews the complete task before the existing prepare and RUN approvals. Tool access must be disabled for this planning entry; arbitrary normal chat responses must not trigger it. Chief deferred this Activity change while completing the essential typed route. No implementation or real model result is claimed.

## Publication, review and next entry

Audit checkpoint `cb2bd728` and early candidate `e2523431` were pushed normally to [draft PR414](https://github.com/Ullmann27/device-ai/pull/414), targeting the Chief branch. Later normal pushes temporarily failed because GH_TOKEN expired (confirmed by gh auth status; no tokens logged or changed by this lane). After authentication recovered, the complete final source was pushed normally: [037370f0544d3ed2ee7686082c41c78046daa64d](https://github.com/Ullmann27/device-ai/commit/037370f0544d3ed2ee7686082c41c78046daa64d). No equivalent-tree substitute was needed for this lane. Evidence/handoff changes after that frozen source are documentation only; verify their current branch HEAD and unchanged production/test hashes from the committed manifest.

The separate ninth Android reviewer froze adversarial cases at `4556be48` before implementation inspection, authored the first test class at `a3e257a1`, and added targeted cancellation/preservation cases at `efa03299`. Byte-exact snapshots and QA hashes of both test classes are retained in evidence with attribution; their combined 13 cases pass on `037370f0`. Final verdict: scoped source ACCEPT, published in [review commit f747661a](https://github.com/Ullmann27/device-ai/commit/f747661a338ad844e6f8b8efb889c1d8fd11fb92) / PR419. Continue from the exact frozen source, verify the independent evidence and published branch, then combine only accepted source into the isolated Chief branch. Main, package/signature, installed APK, owner data and model weights remain untouched. Rollback omits this lane or reverts the three allocated production files together with their scoped tests; old incomplete prepared state must never be automatically promoted or uncertain tools replayed.
