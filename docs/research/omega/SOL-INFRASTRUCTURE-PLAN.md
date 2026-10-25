# Continuum infrastructure repair plan — 2026-10-25

Execution: inline, with independent read-only experiment/CDX audits and a final review. The owner's masterprompt and AGENTS.md authorize investigation, reversible repairs, tests and publication on the dedicated Draft PR #150. No additional approval round is needed for this scope.

Goal: turn PR #148's uncompiled integration into a measured, repaired source checkpoint before extending features. Preserve all model, memory, signer and developer histories.

Architecture: retain the existing Android main dialog, model catalog/runtime, research gates and canonical CCC1/Termux builder. Repair reproduced faults at their existing boundary. A configuration, host test, real model inference and physical-device acceptance remain separate evidence classes.

Tech: Node 24, Kotlin/AGP as pinned in the repository, Gradle 8.11.1, Android SDK 36; authorized local cloud toolchain only.

## Constraints and review focus

- Package `at.heinz.deviceai.mentorpaired`, next owner version 24 / 0.3.23 after independent discovery/verification of published version 23; no signer or release-source-authorization changes.
- No main merge, owner APK signing, installation, paid compute or research-weight promotion.
- Check exact source, malformed/missing pin, dirty checkout, incompatible imported model, failed/cancelled generation, general questions mistaken for owner memories, narrow/wide UI and interrupted workers.
- No new backlogs, shell-from-text or worktree-as-OS-sandbox claims. Existing Qwen remains a fallback until actual Gemma acceptance.

## Tasks and verification ledger

- [x] Audit live required PR heads, Issue #41 and project contracts. Create/publish separate branch and Draft PR #150. Baseline commit: 5d944d49af099a0a66660f5a126c9c7231e0547c.
- [x] Run full Node/backend baseline: Node 1227 passed / 1 failed / 6 skipped; backend 60 passed. Failure: stale no-argument importer signature in android-integration-wiring.test.mjs, confirmed against actual implementation. Preserve integrity assertions and update only function discovery; add runtime widget coverage for the real importer where host support permits.
- [ ] Repair scripts/continuum-model-center-qa.sh exact-source gate. Add executable Node regression fixture for missing/malformed/mismatched pin, matching pin without an unrelated variable, dirty tree and wrong branch. Observe failures before fix; use an exit-73 Java sentinel only after successful source validation to avoid network/toolchain side effects. Require a clean exact pin before QA starts.
- [ ] Compile the current full Android source and execute JUnit/widget tests plus configured NewApi lint. Investigate compiler/test failures before any source edits, write focused regression where meaningful, then verify repaired code.
- [ ] Inspect PR #149 against #148 for safe reuse; reconcile overlapping UI without duplicate presets/actions. Only adopt bounded runtime fixes after tests; keep device-quality caveat.
- [ ] Verify actual original Gemma bytes from pinned official Hub revision; metadata verification alone is insufficient. Record byte count, SHA, revision and independent inference status.
- [ ] Publish complete branch/ref inventory and prioritized experiment reachability with source paths, verified caller chains, tests and remaining device gates. Keep uncertain/failed experiments shadowed.
- [ ] Run final appropriate Node/backend/Android regression, independent code review, publish exact source/test evidence and handoff. Do not equate completed first repair batch with the entire long-term program.

Rollback: revert isolated commits on this working branch, or continue from PR #148's immutable SHA. No changes to persisted user data.
