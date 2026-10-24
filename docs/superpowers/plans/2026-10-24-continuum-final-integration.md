# Continuum final integration implementation plan

> Execution: owner explicitly requested autonomous integration and repair on the existing isolated branch. No additional design approval required for this bounded sprint.

**Goal:** Harden the existing conversation → clarification → reviewed M4 plan → owner confirmation → existing builder chain and connect an explicit chat entry.
**Architecture:** Reuse ContinuumCodingConversation, the two canonical M4 plans, existing Android navigation and Termux/CCC1 job transport. No new worker, receipt system, permission, model promotion or data migration.
**Tech stack:** Kotlin/JUnit, Android/Robolectric, Node test runner, existing Gradle 8.11.1/SDK36.
**Spec:** Owner's CONTINUUM MASTER request, AGENTS.md, docs/CONTINUUM-EXPERIMENTVERTRAG.md, GitHub issue #41 (read 2026-10-24).

## Constraints and alternatives

Only sol/continuum-m5-context-coding-experiments-20261024; baseline c9ae9dcf76df7385486b18aaa2e3e41ddcf4aad2. Keep CTX1/2/3 research isolated (EXP-14); do not alter the failed TEST3 result. Preserve owner memory, weights, signing and premium UI. Scope EXP-00/08/09: strengthen current interfaces and regressions; Android remains primary. Prefer an explicit chat project-plan entry and unsubmitted draft over implicit interception of arbitrary chat or replacement of existing Worker drafts. No build from model/user prose. No installation or owner APK/signing in this sprint.

## Review focus

- Negative or postponed build instructions must invalidate previous confirmation.
- A correction typed after preview must invalidate an open confirmation dialog.
- Confirmation after Activity destruction or slot replacement must not dispatch or clear a newer job.
- Repeated/concurrent approvals must remain single use and revision bound.
- Recreation retains unconfirmed requirements/draft and preserves the pending project identity without restoring execution authorization.

## Tasks

- [ ] Reproduce actual conversation errors with failing JUnit cases; minimally repair the parser and run focused/full relevant tests.
- [ ] Reproduce lifecycle/stale-dialog faults with failing Robolectric widget cases; repair existing Activities without changing layouts or transport.
- [ ] Add explicit Worker project-plan chat command, preserve existing Worker draft behavior, carry a bounded unsubmitted request via existing preview routing. Add command/route and widget regression coverage.
- [ ] Audit CTX/memory/CCC1 boundaries and newer branch differences read-only; integrate only independently justified compatible fixes.
- [ ] Run Node/backend suites, actual Kotlin/Android Gradle unit and widget tests, lint; record all failures/skips and unavailable device checks. Repeat affected checks after fixes.
- [ ] Review combined diff independently; commit and push each functional checkpoint, then record full end-SHA, test evidence, remaining problems, rollback and exact next step in GPT-6 handoff.

Rollback: revert only own new commits in reverse dependency order; no force-push/reset of remote branches. Existing research OFF switch remains available. Phone/Termux hardware acceptance is a separate missing gate, never inferred from host tests.
