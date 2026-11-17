# Code47 response completion repair

Status: capture/agent repair ready for coordinated integration; **54/54 focused host tests PASS**. Integrated Android/APK/device/model acceptance remains **NOT_RUN** for this support branch.

## Source and ownership

- Repository: `Ullmann27/device-ai` (public owner project, verified 2026-11-06).
- Branch: `codex/continuum47-response-completion-20261106`.
- Base: `5572edfbb02570ae479a8f0f72179f5ca33027c5` (PR578).
- Target: `codex/continuum-code47-qa-candidate-20261106`.
- This repair owns `BoundedAgent.kt`, `LocalGenerationDeadlinePolicy.kt`, a pure capture/terminal helper and focused tests.
- `AgentActivity.kt` belongs to the coordinated wiring agent. Integration fragments will be supplied separately; do not edit its file concurrently.
- Context completeness/storage belongs to the context agent. Coordinate `responseComplete` rather than invent another memory store.

## Reproduced source defects

1. Production chat captures up to 20,000 characters, then `AgentModelOutput` rejects complete responses above 8,000 before the existing 20,000-character dialogue quality gate. The generated response is lost.
2. Capture clips oversized deltas without a truncation status. The watchdog treats exactly 20,000 characters as overflow. Callback ordering therefore changes the capture result.
3. A stall returns an empty response although text was already generated.

These are source-level findings on code46 `577f0931e7937ffcac02dc855a540f093facdb3b` and code47 `9ee7535bb131504cc85419afd30f446089a40171`. They do not identify a particular device run without its logs.

## Intended narrow contracts

- Preserve the default 8,000-character model-output limit for tool/planner/coding/bot paths.
- Explicit proposal-free owner dialogue may retain complete text up to the existing 20,000-character chat budget. It must pass the existing quality gate.
- Atomically capture text and terminal status. Exactly 20,000 characters is not evidence of overflow; actual extra text is.
- Preserve incomplete text with an explicit status after overflow/stall/failure, without tool authority or successful learning/evidence.
- STOP, obsolete sessions/model bindings and late callbacks remain invalidating gates.
- Do not invent a native `onDone` finish reason or raise native context/token budgets.
- Preserve PR578's dispatch observer and the existing tool/cancellation contract.

## Tests and acceptance

Initial checkpoint: `3c336a44692125b63b156e902a13a5703f922d8e`, Draft PR581.

Pure capture checkpoint: Kotlin 2.4.0 / JDK17 / JUnit4.13.2, 256MiB compiler heap. The unchanged production deadline policy first failed its exact-cap assertion (4 tests / 1 failure). A pure extraction retaining the original capture `take`, `clear` and `>=` behavior then failed 6 of 17 cases. After correction the same 17 cases pass. Raw commands, baseline extraction and results: `docs/evidence/code47-response-completion-20261106/`.

Owner output checkpoint: real stable SDK dependencies from source `24547da`, freshly compiled `BoundedAgent` and test classes first on the classpath. API-only baseline retained the old 8,000 limit and old partial-result handling: 11 tests / 4 expected failures. The repaired core passed 48 tests across 9 classes, including the unchanged 8,001-with-proposal refusal and M2 dispatch/journal regressions. Exact compile/source manifests and raw JUnit output are versioned alongside the capture evidence.

Final focused rerun: **54/54 PASS**, 10 freshly compiled test classes, Kotlin 2.4.0 / JDK17 / JUnit4.13.2, real stable SDK dependencies, 256MiB heap. The current cancellation diagnostic API is included. Production/test SHA-256 hashes, exact dependency-source SHA, commands, exit codes and raw output are in `final-receipt.json` and adjacent files. The compiler emitted one pre-existing unnecessary-`!!` warning in `BoundedAgentOutcomeEvidenceTest`; no compilation or test failures.

Queued-delivery regression: the old owner gate checked active request identity only. Six real JVM tests exercise a queued Runnable and the production `LocalGenerationDeliveryGuard` with real cancellation/model types. Baseline: 6 tests / 4 failures (STOP, changed session, changed weights, unloaded model). Final: all six pass, including the current-response and newer-request controls. This is a delivery-gate unit test, not an Android UI/device test. The Activity patch invokes the same guard inside `deliverResult`, before clearing `activeChatCancellation`: constructor gets captured turn `localSessionId`; `isCurrent` gets current Activity `sessionId`. Direct source binding: Activity declares `@Volatile private var sessionId`; runLocal captures `val localSessionId = sessionId`.

The helper is not connected to AgentActivity in this support branch. Its exact integration patch is `docs/research/omega/CODE47-RESPONSE-COMPLETION-ACTIVITY-20261106.patch`; `git apply --check` passed on the unchanged base Activity. Only the coordinated wiring agent applies it. Context persistence must use `record(..., responseComplete=run.responseComplete)` for a successful response or accepted partial (`!responseComplete && response.isNotBlank()`); rejected partials use `recordUnanswered`. Remove the old duplicate `rememberContextTurn` write when connecting the context agent's callback.

Integrated Android compilation, APK and device/model validation remain **NOT_RUN** for this repair.

Verified cases: complete 8,000/8,001/20,000-character owner responses retain their ending through the real dialogue quality gate; 8,001 plus proposal still refuses all tool calls; 20,000 with both callback orders; 20,001 overflow; Unicode boundary; partial text followed by stall; late deltas/terminal events after completion and STOP; incomplete replies are evaluated but create no success episode/reputation/outcome. Existing bounded-agent cancellation, outcome and M2 dispatch/journal tests remain green.

No parallel heavy Gradle. Full current-source Android verification is coordinated centrally. Do not promote focused host checks to device/model verification.

## Experiment compatibility and rollback

EXP-05 (local generation), EXP-07 (independent negative tests), EXP-11 (same bounded orchestration) and EXP-12 (context continuity) are affected. No new model authority, job framework, memory promotion or external service. Existing package/signature/version remain unchanged.

Rollback: revert this support branch's repair/integration commits together; never reset or force-push an active shared branch. Next step: complete the coordinated Activity/context wiring, then run one full current-source SDK/Android QA build. Only that integrated result can support APK publication; this focused host result is not a model-quality or Fold7 claim.
