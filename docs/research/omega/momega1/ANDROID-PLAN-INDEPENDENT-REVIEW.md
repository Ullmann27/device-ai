# Independent Android planning-path review

Status: WIP / UNVERIFIED. Baseline19474261c15a3503a9f041244b4b4c3eaed4636f. Frozen before candidate implementation/inspection. Reviewer owns tests/evidence/docs only; author owns AgentActivity plus narrowly scoped helper. Existing ToolGate/runtime/manifest/IPC/scheduler remain outside feature scope. No device access: FOLD7_NOT_RUN.

## Frozen adversarial cases

1. Admission must require explicit owner full lowercase40-character source SHA and1-3 permitted source paths before generation. Missing/partial SHA, invalid count, duplicate/traversal/absolute/forbidden/control paths, mismatched language/chain and invalid limits must block inference and any draft dialog. No invented metadata.
2. Model output is a proposal only. Model cannot choose/change task ID, SHA, source path scope, fixed chain or limits; tool/shell commands and authorization fields never become authority. Valid model goal/kind/acceptance may prefill a typed draft but cannot approve, prepare, dispatch or install.
3. Strict bounded whole-document parsing rejects surrounding prose/fences, duplicate or unknown keys, trailing content, malformed Unicode, numeric overflow, oversized output, excessive goal/criteria count/length, unknown kind, wrong types and omitted required model fields. Rejection must leave no draft/authorization state.
4. Exact owner-scope immutability must survive mutated input collections and generation. A replacement source/path/pin, session switch, new turn or model identity after generation invalidates the old result. Equal display strings are insufficient if the active turn differs.
5. Explicit cancellation during generation and after worker completion but before UI delivery, Activity destruction/recreation, replaced session, switched model, failed/partial generation and unverified model binding must not open a draft dialog. Check on UI delivery, not solely before posting.
6. Planning has no tools/side effects: no ToolGate execution, advisory tools, continuation loop, context-triggered commands, CCC1 writes or direct authorization. Bounded orchestration has zero tool calls/retries; no alternate model/runtime engine is introduced. A malicious model tool call is rejected or shown as inert text only.
7. Existing normal chat path retains its prior orchestrator/tools/context behavior. Explicit planning opt-in cannot silently affect following normal chat turns; no stale planning-mode state leaks after success, failure or cancellation.
8. Full proposal path genuinely reaches the existing manual draft dialog with every owner-bound field and proposed criterion; full existing review and explicit approval are still required. Do not infer model-to-coding integration from an unused helper or test-only adapter. Existing runLocal model/session/turn binding must remain intact.
9. Failure-safe lifecycle: absent model readiness, busy turn, destroyed Activity, superseded generation or missing scope reports failure/blocked state without queueing an auto-run. Multiple/stale completions must not duplicate proposal dialogs or reuse old execution consent.
10. Evidence: reproduce meaningful RED on baseline/new unchanged candidate tests, then GREEN on exact final source. Synthetic/injected model output is CONTRACT_TEST, not local model execution. Host process assertions do not establish Android17/Fold7 execution.

## Execution plan

Inspect actual baseline runLocal and draft paths. Freeze independent test assertions before author implementation outcomes are shared. Start bounded pure Kotlin/parser/controller checks; QA alone schedules Gradle/Robolectric after immutable external test snapshots. Record source SHAs, command exits, counts/skips, raw failure evidence and hashes. No feature edits or central contract changes by reviewer. Rollback is omission of this review-only branch.
