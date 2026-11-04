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

- [x] Write real Git fixture tests for ancestor, identical, missing and divergent source.
- [x] Observe concrete regression tests fail before their corresponding fixes (protected paths, executable filters/configuration masks, report file hash and APK diagnostic connection).
- [x] Implement pinned, bounded, read-only Git comparisons; never run branch code.
- [x] Propose idempotent `BRANCH_REVIEW` tasks in the existing BacklogStore without accepting them.
- [x] Verify that source/target drift changes the proposal identity and protected paths remain review-only.

## Task 2: Existing cadence and executable entry point

Files: new `.github/continuum/branch-bots-cli.mjs` and its behavior tests.

- [x] Add one-shot execution and an explicit finite loop using runTeamleaderLoop.
- [x] Keep report/checkpoint/backlog files outside the source tree and use canonical source identity.
- [x] Run subprocess tests for option validation, meaningful output and unchanged source worktree.
- [x] Document exact commands and the distinction between discovery, integration, build and device acceptance.

## Task 3: Negative ECHO report on strict model rejection

Files: existing `.github/continuum/coding-orchestrator.mjs`, new `.github/continuum/coding-orchestrator-failure-report.test.mjs`.

- [x] Reproduce the skipped-report path without disabling isolation or model admission.
- [x] Ensure reporting runs only after validated canonical task execution and current identity checks.
- [x] Preserve rejection and forbid experience promotion/success on unadmitted inference.
- [x] Run targeted existing reporting, assignment, scheduler and coding regressions; disclose any incomplete-snapshot failures.

## Task 4: Independent integration review and handoff

- [x] Review selected current feature branch changes against pinned source hashes.
- [x] Integrate only verified compatible changes; retain conflicting or unverified candidates in the handoff.
- [x] Independently review security and evidence semantics of Tasks 1–3.
- [x] Publish exact code, tests, test results and remaining APK gates in the Draft PR.

## Final evidence

The implemented Node scope passed 138 targeted tests with no failures or skips. The existing scheduler gained a trusted source-preflight hook; the CLI binds it before all scheduler source checks. The existing read-only APK host inspector is attached to each cycle. Independent review rechecked every concrete reported flaw. Android compilation, device acceptance and live owner-worker activation remain unavailable. Read `docs/research/omega/BRANCH-INTEGRATION-BOTS-HANDOFF-20261104.md` and the exact evidence under `docs/evidence/branch-integration-bots-20261104/`. Unverified Kotlin candidates remain observations rather than merged patches.

## Task 5: Android-first and existing QA connections — continuation

- [x] Compare exact current repair/integration source blobs; keep moving PR metadata separate from pinned source content.
- [x] Reproduce the missing PR505 preflight connection: 14 failures among 43 actual shell-entry fixture cases.
- [x] Adopt the exact two-file version/signing-preflight correction; 43/43 pass and independent review pass.
- [x] Reproduce two Android environment regressions, reuse the existing Termux resolver and pass explicit env to existing probes.
- [x] Independently repeat the 61-test environment/CLI/builder suite.
- [x] Run combined targeted acceptance: 183/183, no failures/skips; preserve the separate APK/device NOT_RUN boundary.
- [x] Record the unchanged local toolchain gap and the historical Render QA source without retargeting or deploying that shared service.

Current continuation evidence: `docs/evidence/branch-bots-continuation-20261104/`. Complete source and actual Android/Gradle/device execution remain required before APK acceptance.
