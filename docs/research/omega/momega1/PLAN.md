# MΩ-1 implementation plan

Status: WIP / no model or device acceptance. User master order of 2026-11-03 authorizes isolated implementation, real agent teams, testing, commits, pushes and research PRs. No further design approval is required. This plan uses the worktree, parallel-agent, planning and independent-review skills under that authorization.

Goal: connect the existing local model, typed coding task, canonical CCC1 worker/verifier and durable experience path; demonstrate the loop only with real local inference and separately authorized real Fold7 execution.

Architecture: preserve AgentActivity.runLocal → AiContextCompiler → existing bounded orchestrator → model → ToolGate and existing coding authorization. Preserve CCC1 journals and LEX3; no competing scheduler, memory database or model service. Prefer the loaded LiteRT runtime only if authenticated, bounded IPC and lifecycle can be demonstrated; otherwise an explicitly approved, hash-verified local GGUF runtime, never a cloud fallback.

Baseline: branch created at verified Code40 source 64ec06a73f50cae9ee49035c840710d87777d50f, then fast-forwarded to observed Chief 786436f58c966366a97941c3fbc949b296b9b4f7 after inventory. Only six production files differ (CCC1 verifier, replay cache, bounded research sanitization/admission, quantity timeline); these are the four already accepted lanes. Their newer combined QA is still running and does not inherit Code40 acceptance. Remote snapshot records #384–408; #384–387 and #402 remain research only. No moving foreign branch is edited.

## Global constraints

- Original package at.heinz.deviceai.continuumlocal, main, original signer, release publisher, owner data and productive model weights unchanged.
- No installation, model download, paid action, production deployment or GitHub Actions.
- Model text is never authorization; complete task is approved with full source SHA, 1–3 source files, 1–8 acceptance criteria, fixed chain and bounded calls/context.
- Missing OS isolation or local model proof blocks execution; worktrees alone are not a sandbox.
- Genuine inference, RED, GREEN, reflection, independent verification, persistence and recall must all be observed before FIRST_COGNITIVE_LOOP_VERIFIED.
- CONTRACT_TEST / HOST_INTEGRATION / LOCAL_MODEL_HOST / FOLD7_MODEL / FOLD7_END_TO_END / RELEASE_READY are separate evidence classes.
- Raw private prompts, device identifiers, credentials and owner information are not committed.
- EXP-00/01/02/03/05/07/08/11/12 apply. Existing experiment and safety contracts remain binding; obsolete research freezes are superseded by the user's current master order.

## Exclusive work packages and review focus

1. ALPHA runtime: inspect LiteRT shared use first; implement smallest safe actual model binding in existing Node provider/CLI if shared runtime is unavailable. Own coding-provider.mjs, scripts/continuum-local-brain*.mjs and new admission helpers/tests. No Android runtime edits without Chief allocation. Test unavailable weights, altered weights/runtime, replay, malformed output, timeout and no network fallback. Report interface before cross-lane wiring.
2. BETA worker: own coding-orchestrator.mjs, coding-agent-loop.mjs, ccc1-redgreen-verifier.mjs and relevant new tests. Connect real diagnostics/independent verification to existing repair path. Preserve owner scope and isolation. Rerun #400/#407 counterexamples; raw failures remain visible.
3. GAMMA experience: own ccc1-experience.mjs and new narrowly scoped recall helper/tests. Extend existing journal proof contract and implement bounded persisted retrieval; no owner-fact type conversion. Test corruption, restart, stale/revoked records and concurrent writers. Coordinate exact provider interface with ALPHA/BETA before wiring.
4. DELTA Android: audit runLocal → typed task → approval → RUN_COMMAND → callback and shared runtime feasibility. Own only new audit documents/tests initially; AgentActivity, ToolGate, model runtime and central contracts remain Chief-controlled. Report smallest concrete change with actual caller evidence.
5. OMEGA-QA: independent verifier and ten frozen unknown synthetic tasks before candidate inspection, private holdouts until freeze. Own tools/momega1-qa, docs/evidence/momega1/qa and build environment. Never certify authored feature code as independent. Coordinate one full Gradle job at a time. No model-generated benchmark success without actual inference.
6. ALPHA/DELTA challenger: independently assess model identity/admission, cancellation, isolation, available device resources and Android process boundary; own new challenge tests/docs only. Review frozen runtime candidate later.

All lanes use separate /workspace/momega1-* worktrees and codex/momega1-*-20261103 branches. At most seven active sessions including Chief. No worker delegates further. Chief owns integration/docs and allocates subsequent memory/repair reviewers in later waves.

## Execution ledger

- [x] Remote HEAD and mandatory handoff inventory; old Code40 evidence retained as rollback only.
- [ ] Finish call-path/design audit and freeze interface decisions in ARCHITECTURE.md.
- [ ] Each lane: reproduce RED on unchanged base; record source SHA, command, exit and test identities; implement minimal GREEN; commit and push.
- [ ] Separate reviewer supplies independent adversarial evidence on each frozen candidate; integrate only accepted paths.
- [ ] Host integration with real synthetic source/test execution, explicitly labelled contract fixtures where model is simulated.
- [ ] Actual local-model experiment only if owner-approved weights/runtime are available; otherwise BLOCKED_LOCAL_MODEL_UNAVAILABLE.
- [ ] Restart/recall and paired experience ablation with actual model when available; never infer learning benefit from storage alone.
- [ ] Final exact-source Android JUnit, Node, backend, configured lint, unsigned build; preserve all failures/skips.
- [ ] Authorized Fold7/Android17/ARM64 identity, offline transports, lifecycle, measurements and ten-task acceptance; otherwise FOLD7_NOT_RUN.
- [ ] Publish exact commits, evidence matrix, limitations and restart/rollback instructions in HANDOFF.md.

Rollback: omit/revert isolated research changes. Code40 source remains intact; no automatic LEX3 downgrade or stale owner-data restore. On uncertain execution, inspect journals and block replay rather than repeating tools.
