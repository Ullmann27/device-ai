# MΩ-1 Android integration handoff

Status: WIP / SOURCE_AUDITED; device and native inference NOT_RUN. Base `ca93fb4f`; isolated branch `codex/momega1-android-20261103`. EXP-00/01/02/03/05/07/08/11/12 apply. Chief explicitly allocated BuilderActivity, ResultReceiver, TypedCodingBridge and focused tests after this audit. AgentActivity, ToolGate and LiteRT remain outside this lane.

## Actual call path and missing transitions

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
