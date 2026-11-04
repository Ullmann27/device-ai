# Continuum Branch Integration Bots Implementation Plan

> For agentic workers: implementation is split into isolated worktrees and independently reviewed. The owner has already instructed execution.

**Goal:** Add reusable branch discovery to the existing development machinery and repair a proven missing negative bot report.

**Architecture:** Git refs and source trees produce source/target-bound findings. Review tasks use BacklogStore; scheduling uses runTeamleaderLoop. CCC1 retains all code-change and verification authority.

**Tech Stack:** Existing Node.js ES modules, Git CLI, node:test; no new package dependency.

**Spec:** `docs/superpowers/specs/2026-11-04-branch-integration-bots-design.md`.

## Global Constraints

- Product source baseline `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.
- No main merge, candidate code execution during discovery, new worker ledger, automatic task acceptance, signing or install.
- Local test material is a selected-file snapshot, not a complete Android checkout. Every retrieved input is checked against its Git blob SHA.
- Unavailable Android execution remains a failed readiness gate, never an inferred pass.

## Review Focus

- Branch aliases, remote symbolic HEADs and moving refs must not produce stale or duplicate authority.
- Divergent source, deleted paths and protected test/control-plane paths must remain review-only.
- Repeated scans must not crowd the canonical backlog with equivalent tasks.
- Cancellation, corrupt state and process restart must preserve existing scheduler bounds.
- Model rejection and revoked reporter identity must not create success, experience admission or a forged report.

## Task 1: Bounded branch discovery and canonical backlog proposals

Files: new `.github/continuum/branch-discovery.mjs`, `.github/continuum/branch-discovery.test.mjs`.

- [ ] Write real Git fixture tests for ancestor, identical, missing and divergent source.
- [ ] Observe the new tests fail before implementing the discovery functions.
- [ ] Implement pinned, bounded, read-only Git comparisons; never run branch code.
- [ ] Propose idempotent `BRANCH_REVIEW` tasks in the existing BacklogStore without accepting them.
- [ ] Verify that source/target drift changes the proposal identity and protected paths remain review-only.

## Task 2: Existing cadence and executable entry point

Files: new `.github/continuum/branch-bots-cli.mjs` and its behavior tests.

- [ ] Add one-shot execution and an explicit finite loop using runTeamleaderLoop.
- [ ] Keep report/checkpoint/backlog files outside the source tree and use canonical source identity.
- [ ] Run subprocess tests for option validation, meaningful output and unchanged source worktree.
- [ ] Document exact commands and the distinction between discovery, integration, build and device acceptance.

## Task 3: Negative ECHO report on strict model rejection

Files: existing `.github/continuum/coding-orchestrator.mjs`, new `.github/continuum/coding-orchestrator-failure-report.test.mjs`.

- [ ] Reproduce the skipped-report path without disabling isolation or model admission.
- [ ] Ensure reporting runs only after validated canonical task execution and current identity checks.
- [ ] Preserve rejection and forbid experience promotion/success on unadmitted inference.
- [ ] Run targeted existing reporting, assignment, scheduler and coding regressions; disclose any incomplete-snapshot failures.

## Task 4: Independent integration review and handoff

- [ ] Review selected current feature branch changes against pinned source hashes.
- [ ] Integrate only verified compatible changes; retain conflicting or unverified candidates in the handoff.
- [ ] Independently review security and evidence semantics of Tasks 1–3.
- [ ] Publish exact code, tests, test results and remaining APK gates in the Draft PR.
