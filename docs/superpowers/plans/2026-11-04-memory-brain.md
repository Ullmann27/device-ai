# Memory and brain implementation plan

> **For agentic workers:** use bounded independent tasks and preserve the
> behavioral RED→GREEN and whole-branch review gates. No publishing workaround.

**Goal:** strengthen the real memory/context path and connect genuine activity
to the avatar's existing event interfaces.

**Architecture:** amend existing cognitive components and add one adapter to
the current memory hub. Preserve the current ledger, local model and UI owners.

**Tech Stack:** Kotlin 2.4, Android SDK 36, JUnit 4, existing Gradle toolchain.

**Spec:** `docs/superpowers/specs/2026-11-04-memory-brain-design.md`.

## Global constraints

- Base 578bdfd1, own branch only; no main merge, force push or shared edits.
- No model-weight/training, recovery-ledger, UI or Activity changes here.
- No new DB, orchestrator, external model calls or fabricated activity.
- Current GitHub visibility is public by explicit owner choice. The owner
  reconfirmed publication of project changes and coordination on 2026-11-04;
  this resolves the first-push rejection caused by historical private metadata.
- Raw logs and source hashes; distinguish host tests from device acceptance.

## Review focus

- Imported memory text can contain role delimiters, source IDs and late negation.
- Duplicate memory IDs can disagree even when only one copy is privacy-visible.
- Collections returned by stores can be cast to mutable collections.
- Listener close may race event delivery or arrive after a screen is replaced.
- A zero-hit or failed operation must never look like successful learning.

## Task 1 — Evidence context

Files: `cognitive/AiContextCompiler.kt`, new `MemoryContextBoundaryTest.kt`.
Interface remains `compile(AiContextInput): CompiledAiContext`.

- [x] Reproduce raw memory role/control-token injection and missing evidence frame.
- [x] Encode memory metadata/content using the existing complete sanitizer and
  explicit evidence framing, preserving whole-group budgeting and source IDs.
- [x] Check late corrections, Unicode, privacy, secret redaction and byte limits
  with existing ContextCompiler/ContextIntegrity tests; commit local checkpoint.

## Task 2 — Retrieval and store integrity

Files: `cognitive/HybridMemoryRetriever.kt`, `cognitive/MemoryEcology.kt`, focused
new tests and the existing memory research gate/challenge caller.
Public retrieve/store signatures stay source-compatible.

- [x] Reproduce confirmed audit findings before production changes.
- [x] Make ambiguous IDs fail closed and stored evidence/experience snapshots
  independent from caller mutations; preserve validation and ranking contracts.
- [x] Optimize only behavior-preserving retrieval work verified against a
  deterministic reference and measured synthetic corpus.
- [x] Run unchanged relevant suites and record actual before/after metrics.

## Task 3 — Actual memory source for the avatar

Files: new `cockpit/ContinuumMemoryEventSource.kt`, focused integration tests.
Consumes `ContinuumMemoryEvents`; produces existing `MemoryEventSource`.

- [x] Test actual hub recall/storage/document dispatch with main-thread mapping,
  listener failures, close/race/isolation and no source-text exposure.
- [x] Implement small lifecycle-bound adapter; use actual finite counts/scores.
- [ ] Provide the exact factory/hook contract to the visual integrator and
  validate the combined path once its source is available.

## Task 4 — Integration and handoff

- [ ] Independent review and fixes with reproducing tests.
- [ ] Run relevant complete regression, Android compilation and lint on frozen
  combined source; compare other workstream heads and changed paths afresh.
- [ ] Build APK only when all requested workstreams have finished and their
  integration gate is satisfied; record exact package/version/source/hash.
- [ ] Preserve raw logs, patches, source heads, remaining device gates and a
  resumable handoff. Never label local work as pushed or an unsigned QA build
  as an installable original-signed update.
