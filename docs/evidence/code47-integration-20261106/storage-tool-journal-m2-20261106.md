# Code47 M2 – real storage-tool receipt into the existing work journal

Status: **SOURCE INTEGRATED; FOCUSED JUNIT NOT RUN; FOLD7 NOT RUN**.
This is an engineering checkpoint, not a signed Code47 APK.

## Actual production wiring in Draft PR #576

- `BoundedAgentOrchestrator` accepts a non-authoritative observer callback
  `onToolStarted(callId, toolName)`. It fires **only from ToolGate's actual
  `onExecutionStarted` callback**, not when the model proposes or imagines a tool.
  The observer is wrapped in `runCatching`; it cannot authorize an action.
- Both the deterministic `executeBootstrapReadOnly` path and the owner
  `runLocal` path in `AgentActivity` attach the observer for exactly
  `get_storage_info`. Assigned bot and coding-planning paths are excluded.
- The existing serial `documentIoExecutor` persists a TOOL episode in
  `CockpitWorkJournal` under a deterministic, call-ID-derived entry key.
  At the end of the actual orchestrator run, only matching
  `AgentTaskEvidence.calls` receipts determine the terminal status.
  Missing proof, STOP/session change, replay, non-fresh output, and uncertain
  outcome must remain UNKNOWN; READ_ONLY policy denials are REJECTED, not
  performed. Tool results are labeled **not independently confirmed**.
- Only the trusted call ID, tool name, bounded status, and optional SHA-256
  output fingerprint are kept; no tool arguments, result bodies, secrets,
  device readings, new permissions or raw model text are persisted.
- Existing PDF documents remain `Kind.DOCUMENT`; new `Kind.TOOL` has
  its own explicit `ToolState`. `WorkDecisionRecall` excludes tool
  episodes from claims of owner decisions. `WorkRecall` can show their
  truthful status and any open verification step.
- Recovering an interrupted app process changes EXECUTING tool episodes
  to UNKNOWN, **never SUCCEEDED**. Forty original document episode slots
  are retained alongside up to ten small tool slots in the same journal,
  subject to the unchanged 256 KB overall file budget.
- The original `ToolGate` policy, OFF/ASK/ALWAYS_ON gates, STOP mechanism,
  package, original signer, model/runtime and Render release scripts were
  not replaced or relaxed.

## Checked-in test coverage (not yet executed with Gradle)

- `ToolWorkJournalBridgeTest`: durable receipt, idempotence, rejected
  without dispatch, STOP, replay, timeout, missing fresh output, interrupted
  restart, backwards-compatible document rows, invalid IDs and hashes,
  and repeated storage probes preserving PDF history.
- `ToolWorkJournalIntegrationTest`: actual `BoundedAgentOrchestrator`
  + `ToolGate` dispatch before the fake test runner output; durable
  fresh receipt; denied tool has **zero backend executions** and cannot
  be called a success.
- These files are JUnit source, not an observed PASS receipt until
  run in the normal Android SDK/Gradle environment on the exact commit.

## Independently run narrow smoke

A local Kotlin/JVM 1.9.0 executable in the session compiled the
`ToolWorkJournalBridge` classification logic with *minimal stand-in*
`AgentToolEvidence`, `ToolExecutionStatus` and journal signatures:
**11/11 PASS, 0 FAIL** for successful fresh SHA-bound output,
missing/invalid hashes, stale session, outcome uncertainty, replay,
unscoped tool, rejected, failed, cancelled and timeout.
This verifies **only the narrow selector**, not the real production
source tree, JSON persistence, full ToolGate, Android/One UI,
Fold7 or a genuine phone storage measurement.

A read-only lexical bracket/comment/string scan of the live GitHub
journal, bridge, orchestrator, Activity and two test sources found no
unbalanced delimiters. This is **not a Kotlin compile**.
A missing closing parenthesis in the journal sanitizer was spotted
and corrected before the final receipt.

## Required acceptance

1. Run production Gradle/JUnit, including new bridge/ToolGate tests,
   original document journal tests and all existing Chief/STOP tests.
2. Reproduce an authorized **real Fold7** read-only `get_storage_info`
   call from the native UI with ToolGate policy and record exactly one
   started episode and its actual fresh receipt.
3. Test denial (no backend call), repeated call ID (no duplicate side
   effect), STOP during native dispatch (unknown, not success), process
   kill/reopen (unknown), and multiple probes without PDF history loss.
4. Confirm actual model run/CPU/RAM, source SHA, JUnit reports, and
   no signing/publisher changes. Only then mark M2 accepted.

The original Code46 original-signed publisher remains separate and
must finish all its own gates before a download is claimed.
