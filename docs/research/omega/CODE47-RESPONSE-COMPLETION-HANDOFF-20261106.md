# Code47 response completion repair

Status: **WIP / UNVERIFIED**. No device/model/APK verification claimed.

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

Current checkpoint: **NOT_RUN**; restored toolchain is being prepared centrally.

Prepare real Kotlin/JUnit RED -> GREEN for complete 8,000/8,001-character owner responses; 8,001 plus proposal still refuses all tool calls; 20,000 with both callback orders; 20,001 overflow; partial text followed by stall; late deltas/terminal events after completion and STOP; incomplete replies passing through quality evaluation but producing no success episode.

No parallel heavy Gradle. Full current-source Android verification is coordinated centrally. Do not promote focused host checks to device/model verification.

## Experiment compatibility and rollback

EXP-05 (local generation), EXP-07 (independent negative tests), EXP-11 (same bounded orchestration) and EXP-12 (context continuity) are affected. No new model authority, job framework, memory promotion or external service. Existing package/signature/version remain unchanged.

Rollback: revert this support branch's repair/integration commits together; never reset or force-push an active shared branch. Next step: commit tests, record actual RED, implement the smallest matching helper/output contract, supply exact Activity integration and re-run focused tests.
