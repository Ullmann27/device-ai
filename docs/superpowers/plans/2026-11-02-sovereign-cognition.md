# Sovereign cognition implementation plan

> Execute inline with superpowers:executing-plans. Owner explicitly requests autonomous work; design and execution approvals are already supplied by the master directive.

**Goal:** Preserve complete evidence in the existing model context and expose actual task/verification events from the existing orchestrator.
**Architecture:** Atomic compiler sections, existing HybridMemoryRetriever and location verifier, existing BoundedAgent/ToolGate/Experience pipeline. No model manager or memory replacement.
**Tech stack:** Existing Kotlin/JVM/Android and Node QA.
**Spec:** docs/research/omega/SOVEREIGN-COGNITION-HANDOFF-20261102.md plus owner's S1–S3 directive.

## Global constraints

No main/merge, APK build/sign/install, productive weights or personal data. No edits to PR354 Android-click files or PR356 GGUF/Termux files. Host and device evidence remain separate. No extra authority from model, web, memory or plan.

## Review focus

- A late correction must never disappear from a memory advertised as included.
- Negative evidence in another selected record must not be dropped while a positive survives.
- UTF-8 byte and final model character budgets must both preserve the complete compiled context.
- Missing consent, replay, cancellation and unverified evaluation must not become outcome confirmation.
- Evidence metadata contains identities/digests, no raw owner/screen content.

### Task 1 — S1 atomic model context

Files: AiContextCompiler.kt, AgentActivity.kt and AiContextCompiler integrity tests. Returns existing CompiledAiContext with IDs only for complete evidence groups. Required user request, task correction and full contradictions must fit or compilation fails. Optional memory/research groups fit completely or are omitted. No semantic content clipping within evidence. Activity must reject final prompt clipping and not swallow required-context errors.

- [x] Add real late-negation, ID/body mismatch, partial request, contradiction precedence, atomic research and final-cap regressions.
- [x] Run RED against unchanged production code and preserve output.
- [x] Implement bounded whole-section compilation; retain existing redaction/provenance gates.
- [x] Run focused GREEN then independent complete main/test compilation; commit/push S1.

### Task 2 — S2 prospective context ablation

Files: frozen-context-holdout.json, harness under tools/continuum-span-research, full row reports in evidence directory. Corpus frozen at start checkpoint before any candidate changes. Baseline parent source and candidate use identical fixture and verifier. Seeds 113/227/409 change synthetic bindings, not model training.

- [x] Commit immutable holdout SHA before candidate implementation; never tune on it.
- [x] Evaluate full-source oracle, old compiler, candidate, and no-memory control on every row.
- [x] Report exact-correct, wrong, abstain, paired improvements/regressions; count positive coverage loss.
- [x] Explain CDX1 capacity/conditioning/teacher-forcing hypotheses separately; no model-quality claim from a compiler experiment. Commit/push S2.

### Task 3 — S3 observed cognitive task evidence

Files: BoundedAgent.kt, CognitiveTurnTrace.kt and task evidence tests; reuse ReadOnlyToolPlan and DeviceStorageProbe. Task identity comes from existing trusted callIdPrefix; no second task store. Structured result separates proposed plan, approval request/decision, fresh receipt/output digest, evaluator scope/postcondition and learning candidate. Runtime phases come only from actual events.

- [x] Reproduce missing event/proof and permissive-evaluator false completion cases.
- [x] Add minimal task-bound metadata and events in existing runtime.
- [x] Exercise real host filesystem probe; independently inspect persisted receipt and JDK FileStore; negative policy/replay/cancellation controls.
- [x] Demonstrate ExperienceCompiler review-only candidates using distinct real runs, no activation. Commit/push S3.

### Completion

- [x] Run relevant complete Kotlin host and critical Node checks; inspect all outputs.
- [x] Fresh reviewer reads whole diff and reports gaps; repair material findings with RED/GREEN.
- [x] Update handoff with per-milestone commit SHAs, logs, actual paths and owner's ten questions. DEVICE NOT_RUN.


Final ledger: S1 c1af4339 complete; S2 c6f79e0e complete; S3 0e22bdb7 complete; single review fix aabd8ea7 and stronger source-contract babba332. Final verification:1648JUnit+4Checks;135critical Node;1938full Node/13SKIP;72frozen replay. See handoff for all original failures and explicit limits. Owner chose isolated branch + Draft PR, no merge/release; that integration choice is preserved. Ruling: no additional design/integration permission loop because the detailed owner directive explicitly authorized implementation, commit/push and draft. Cost/limit: architectural scope is deliberately narrow and synthetic/device claims remain separate. Native SDK/model/device checks remain NOT_RUN; no discarded minor findings.
