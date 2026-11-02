# Continuum JARVIS integration implementation plan

> **For agentic workers:** Use superpowers:executing-plans; implement each task with RED→GREEN and independent final review.

**Goal:** Make the existing PR360 build and cognitive task path verifiable without changing owner data or claiming device results.

**Architecture:** Reuse canonical QA/JUnit parsing, LocalModelRuntime, ToolGate and file receipts. Integrate complete evidence and durable unknown outcomes into their existing callers.

**Tech Stack:** Node 22, Kotlin 2.4, JDK17, Gradle8.11.1, Android SDK36.

**Spec:** PR360 master comment 6585443037 and SOL61-JARVIS-INTEGRATION-HANDOFF-20261102.md.

## Global constraints

- Isolated Draft PR361; no main merge or parallel-branch replacement.
- Original package at.heinz.deviceai.continuumlocal; code38/version0.3.37 unsigned QA only.
- Original signer and exact-SHA freeze-lift gates remain; no installation or public APK.
- Frozen historical blobs and CDX holdouts unchanged; host and Fold7 evidence distinct.

## Review focus

- Empty/skipped mandatory JUnit suites must fail despite other successful suites.
- Old, future-dated, malformed or symlinked receipts must not establish acceptance.
- Escaped multilingual tool output and labels count toward complete continuation budgets.
- Cancellation/timeout after dispatch must remain UNKNOWN across restart; no replay/learning.
- Lost receipt persistence must block dispatch or retain an ambiguous recovery state.

### Task 1 — Fresh exact-source build evidence

Files: existing render-continuum-integration-qa.sh, continuum-atomic-acceptance.sh/tests; shared scripts/continuum-qa-evidence.mjs/tests.

- [ ] Reproduce acceptance of empty/skipped critical suites and mismatched Node source receipts.
- [ ] Reuse repair-diagnostics parsing for bounded per-suite execution/freshness validation and validate exact-source Node receipts.
- [ ] Integrate both callers, execute focused Node tests, commit/push.
- [ ] Run real SDK Gradle compilation, all JUnit/lint and unsigned original-package assembly; preserve failures.

### Task 2 — Complete model-neutral continuation

Files: existing AgentActivity.kt and localmodel/LocalModelRuntime implementations; bounded continuation helper and JUnit tests.

- [ ] Reproduce late-evidence truncation/escaped UTF-8 overflow.
- [ ] Build complete observations or controlled clarification; use the same bounded policy in existing runtime adapters.
- [ ] Test two host adapters and negative controls; no real second-model device claim.
- [ ] Run relevant Kotlin/Node tests, commit/push.

### Task 3 — Durable task outcome and handoff

Files: AgentToolLayer.kt, ToolExecutionReceiptStore.kt, existing BoundedAgent evidence path and JUnit tests; canonical handoff/raw evidence.

- [ ] Reproduce UNKNOWN becoming ordinary cancellation/timeout after restart.
- [ ] Persist dispatch commitment before backend entry and UNKNOWN on unresolved post-dispatch termination; suppress replay.
- [ ] Verify cancel-before-start, timeout, late callback, parallel calls, failed persistence and legacy receipts.
- [ ] Run final complete host checks, independent review, source-bound report and owner Fold7 update protocol; commit/push.

Signing/device execution remains NOT_RUN unless its actual prerequisites become available.
