# Sovereign cognition — live handoff

Owner-authorized isolated continuation. Initial baseline: PR354 `6fc252ecc05d49508023efe2835a983c14299074`; main observed `2949893a7c65bdc6a875bdcb28b7a550fd9d7c3a`. Exact current PR metadata is preserved in `docs/evidence/sovereign-cognition-20261102/github-inventory.json`. Descriptions are historical assertions, not verification receipts.

## Scope and decisions

One existing brain, memory store, model host and ToolGate. EXP-04/05/06/07/11/12 apply. No main merge, APK build/sign/install, weight/personal-data modification, external model dependency, Android-click or GGUF/Termux edits. The owner's detailed directive explicitly authorizes autonomous implementation and pushes; no additional design approval is needed.

S1: protect the actual `AgentActivity → AiContextCompiler.compile` path against partial evidence and missing correction context. S2: freeze new budget/correction controls before implementation, compare parent and candidate through the existing location verifier; disclose synthetic scope, coverage loss and negative results. S3: extend existing `BoundedAgentOrchestrator → ToolGate → DeviceStorageProbe → evaluator → ExperienceCompiler` receipts with task-bound runtime events; never derive authority from a plan.

Alternatives: increasing prompt budgets only moves the failure; introducing another memory/planner duplicates authority. Chosen approach: preserve whole evidence and fail closed on an incomplete current request or contradiction set, then expose observed outcomes from existing orchestration.

## Initial checklist

- [x] GitHub heads and required issues fetched; official private checkout available.
- [x] AGENTS, experiment contract/register and current entrypoint/matrix read.
- [x] Existing compiler, cognitive runtime, verifier, bounded orchestrator, read-only plan and model interfaces inspected.
- [ ] S1 RED/GREEN and independent rerun.
- [ ] S2 frozen evaluation with raw per-case results and baselines.
- [ ] S3 real host read-only task with negative controls and runtime evidence.
- [ ] Whole-source host verification and final review.

All new functionality: NOT_RUN at this checkpoint. Fold7/device/model: NOT_RUN. No general intelligence or independently trained base model claim. Rollback: revert only this branch's scoped commits.

## S1 checkpoint

SOURCE_INTEGRATED; focused HOST_VERIFIED: 8/8 JUnit. Initial 6-case RED: 5 failures, 1 control passed; full original logs preserved. Compiler now keeps selected memories/research atomic, includes IDs only on successful append, prioritizes full request/task/contradictions and fails closed if mandatory context cannot fit. Activity propagates compilation failure and checks the complete context survives final prompt assembly. Secrets still redacted. Model/permission/weight interfaces unchanged. Whole-source compile/test rerun is pending at this commit, not inferred from focused tests. The first host setup found missing javac launcher; the installed jdk.compiler module is used through a local wrapper. A fixture constructor typo was corrected before the genuine five assertion failures were recorded.
