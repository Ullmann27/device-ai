# Sovereign cognition — live handoff


**Abschluss:** [Bericht mit allen zehn Antworten](SOVEREIGN-COGNITION-ABSCHLUSS-20261102.md). Finale Laufzeitquellen aabd8ea7 / Abnahmestand babba332, 727 Compiler-/Testinputs hashgleich bis zum Abschluss. **1648 JUnit/303 Klassen +4 Checks PASS; kritische Node135 PASS; vollständige Node1938 PASS/0FAIL/13SKIP; finaler 72er-Replay48→72; drei frische reale Host-Proben und Review-Kandidat bestätigt.** Native Gradle/LiteRT/Gemma/Fold7/S4 NOT_RUN. Sämtliche echten Fehler, Gegenbeispiele, Review und unbekannten Ursachen bleiben erhalten. Maschinenlesbare Quittung: `docs/evidence/sovereign-cognition-20261102/final-verification.json`. Pro Meilenstein eigene Datei `SOVEREIGN-S1/S2/S3-HANDOFF-20261102.md`. Der folgende Verlauf enthält historische Zwischenstände, keine verbleibenden pauschalen NOT_RUN-Behauptungen für die jetzt ausgeführten Hostchecks.

Owner-authorized isolated continuation. Initial baseline: PR354 `6fc252ecc05d49508023efe2835a983c14299074`; main observed `2949893a7c65bdc6a875bdcb28b7a550fd9d7c3a`. Exact current PR metadata is preserved in `docs/evidence/sovereign-cognition-20261102/github-inventory.json`. Descriptions are historical assertions, not verification receipts.

## Scope and decisions

One existing brain, memory store, model host and ToolGate. EXP-04/05/06/07/11/12 apply. No main merge, APK build/sign/install, weight/personal-data modification, external model dependency, Android-click or GGUF/Termux edits. The owner's detailed directive explicitly authorizes autonomous implementation and pushes; no additional design approval is needed.

S1: protect the actual `AgentActivity → AiContextCompiler.compile` path against partial evidence and missing correction context. S2: freeze new budget/correction controls before implementation, compare parent and candidate through the existing location verifier; disclose synthetic scope, coverage loss and negative results. S3: extend existing `BoundedAgentOrchestrator → ToolGate → DeviceStorageProbe → evaluator → ExperienceCompiler` receipts with task-bound runtime events; never derive authority from a plan.

Alternatives: increasing prompt budgets only moves the failure; introducing another memory/planner duplicates authority. Chosen approach: preserve whole evidence and fail closed on an incomplete current request or contradiction set, then expose observed outcomes from existing orchestration.

## Initial checklist

- [x] GitHub heads and required issues fetched; official private checkout available.
- [x] AGENTS, experiment contract/register and current entrypoint/matrix read.
- [x] Existing compiler, cognitive runtime, verifier, bounded orchestrator, read-only plan and model interfaces inspected.
- [x] S1 RED/GREEN and independent rerun.
- [x] S2 frozen evaluation with raw per-case results and baselines.
- [x] S3 real host read-only task with negative controls and runtime evidence.
- [x] Whole-source host verification and final review.

All new functionality: NOT_RUN at this checkpoint. Fold7/device/model: NOT_RUN. No general intelligence or independently trained base model claim. Rollback: revert only this branch's scoped commits.

## S1 checkpoint

SOURCE_INTEGRATED; focused HOST_VERIFIED: 8/8 JUnit. Initial 6-case RED: 5 failures, 1 control passed; full original logs preserved. Compiler now keeps selected memories/research atomic, includes IDs only on successful append, prioritizes full request/task/contradictions and fails closed if mandatory context cannot fit. Activity propagates compilation failure and checks the complete context survives final prompt assembly. Secrets still redacted. Model/permission/weight interfaces unchanged. Whole-source compile/test rerun is pending at this commit, not inferred from focused tests. The first host setup found missing javac launcher; the installed jdk.compiler module is used through a local wrapper. A fixture constructor typo was corrected before the genuine five assertion failures were recorded.

## S2 prospective context evaluation

S1 commit: `c1af4339` (full SHA in paired report). Frozen protocol commit: `eefbd231`. Same 72 cases, same existing verifier, three input-binding seeds, no fitting or weight writes: parent **48 correct / 24 wrong / 0 abstentions**; candidate **72 correct / 0 wrong**, including **24 correct negative abstentions** and **48 correct positive answers**. Paired **24 improved / 0 regressed / 48 unchanged**. Full-text oracle 72 correct; empty-context control 24 correct (all negative cases). These synthetic tasks are author-generated prospective controls, NOT independently authored blind tasks or measured Gemma/CDX generation. Candidate was fixed before this evaluation and was not tuned afterward. Tight-budget loss remains possible; the unit test deliberately demonstrates safe whole-group omission. No general quality percentage, CDX repair or device result inferred.

The existing neural-conditioning collision audit is separate and diagnostic, not a trained candidate. No productive checkpoint is loaded or modified. Whole-source S1 host/critical checks run in an isolated detached worktree to prevent concurrent source changes from invalidating evidence.

S2 independent fresh harness rerun against separately compiled full parent/candidate trees reproduced exactly the 48→72 result and the negative collision finding. Class/corpus SHA256 preserved in `s2-independent/inputs.sha256`. No candidate change followed holdout evaluation.

S1 whole-source Kotlin2.4/JDK21 compile passed (compile-only LiteRT stand-in, not Gradle). 1,635 JUnit/300 classes +4 Checks ran: initial run had one obsolete truncation-expectation failure. Stronger whole-literal assertion retained all injection/privacy checks; a rerun passed that contract but exposed a different existing `OwnerMemoryLocationSpecialistTest` plain-retrieval assertion. It is outside the changed compiler path; cause and baseline repeatability are being investigated. Neither run is reported as a fully green suite. Raw outputs retained.

## S3 runtime checkpoint

SOURCE_INTEGRATED / focused HOST_VERIFIED: 7/7 JUnit. Genuine REDs preserved: identical goal/time in distinct existing task namespaces collided in the original experience ID; cancellation could leave a previous EXECUTING phase visible (2 additional regression failures). Experience IDs now include the existing trusted call namespace, and terminal cancellation/failure overrides earlier phases.

Existing `BootstrapBrain → ReadOnlyToolPlan → BoundedAgentOrchestrator → ToolGate → DeviceStorageProbe` executed **three actual host filesystem measurements** in disposable directories. Existing durable receipts were independently reread; output digest, JDK FileStore total and observed timestamp bounds checked. `ExperienceCompiler` produced one review-only candidate from three distinct episodes; no activation, training or owner-memory write. Read-only policy approval is recorded as NOT REQUIRED, not forged owner confirmation. A separate CONFIRM proposal was denied and never executed. Replay, disabled policy, form-only evaluation, timeout, observer exception and cancellation cannot emit verified completion/learning candidate.

`AgentTaskEvidence` is an informational result over existing task/receipt/experience identities, not a second ledger or authority. It distinguishes planned tool names, registered proposal/argument fingerprint, approval request/decision, returned status, fresh output digest, evaluator scope, verified outcome and review candidate. The existing Android caller passes `CognitiveTurnTrace`; task/plan/approval/actual-worker-start/evaluation/outcome events reach that same trace. A new optional ToolGate observation hook runs inside the authorized worker, after policy checks; policy logic is unchanged. The state enum supplies all requested labels; LISTENING/SPEAKING are not fabricated by this task path and remain tied to existing voice callbacks. No avatar or general-purpose planner is claimed.

The unrelated owner-location assertion did not reproduce in eight isolated parent runs (32 assertions/cases PASS). Parent and S1 `ContinuumLearningCore.class` are byte-identical (SHA `b0984df34fb0c20013dbb9618af15a30c3944d0f23c0f0f50847af0cceb5dbaa`); the failing test uses that path, not the changed compiler. Root cause remains UNKNOWN; keep the failed broad-run log and do not weaken its assertion. Final fresh-source complete suite remains required.

## Review repair checkpoint

Fresh read-only review found three Important issues, no Critical/Minor: callback-delayed backend start after timeout, internal advisory context/task clipping, upstream 7900-character task clipping. All are addressed in one scoped fix pass: per-execution eligibility/deadline checked after observer and invalidated before timeout cancellation; optional advisory skips before inference if whole context/task cannot fit; oversized main tasks clarify before busy/inference, retaining the full accepted request. Event/trace append order is synchronized and nonterminal notifications cannot supersede a terminal phase; cross-thread callback consumers must use task/attempt/sequence rather than arrival time. An already-running external operation still needs cooperative cancellation; no OS sandbox/rollback claim.

A further genuine RED showed secret redaction could consume the appended research closing marker. Newline boundaries now keep source/synthesis closing frames intact. Focused review suite: **30/30 PASS** after five genuine failures in 13 pre-fix cases; initial fixture mistake and host friend-module setup failure are explicitly separate from product failures. No tests removed. Final exact-source whole-suite rerun follows; previous source0e22 had **1642 JUnit/302 classes+4 Checks PASS**, critical135 PASS, full Node1938 PASS/0FAIL/13SKIP. These previous PASS receipts are not substituted for the review-fix source.

S1 milestone commit `c1af43392af6efa208b36724771fb7c0892c5deb`; S2 `c6f79e0e0c91ab6ca5a5ca062eaa8568071dac00`; S3 `0e22bdb75124e46dce14f4d46977bedcb235751c`. Full source/host/model/device boundaries and next experiment remain in the research result and architecture matrix.
