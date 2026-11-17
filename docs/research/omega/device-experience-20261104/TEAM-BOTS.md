# Team 1 — bot roster and durable storage

Status: WIP / UNVERIFIED at assignment. Base source: 337ab0195434c8657bf131bf431f4c391aacc28c (PR477).

## Requirements
Read AGENTS.md, docs/CONTINUUM-EXPERIMENTVERTRAG.md, relevant EXP-11/12 entries in docs/CONTINUUM-EXPERIMENTE.json and PLAN.md global constraints. Fix the owner-reported bot-roster storage failure in the existing ChiefBotProfileStore and real ContinuumBotRosterDialog caller. Investigate actual path validation first. Android-managed aliases above the application files directory must be accepted through a bounded trusted-root contract; malicious links within the bot store must remain rejected. Preserve existing registry bytes, identities, schemas, privacy boundaries and diagnostics on corruption, unsupported schema or write failure. Do not reseed or overwrite damaged owner data.

## Ownership
Own app/src/main/java/at/heinz/deviceai/chief/ChiefBotProfileStore.kt, app/src/main/java/at/heinz/deviceai/ContinuumBotRosterDialog.kt, related chief storage tests and roster caller UI tests. AgentActivity.kt is coordinator-owned: report exact requested wiring, do not edit it. No model, worker, scheduler, release or permission changes.

## Acceptance
Exercise real temporary filesystem roots, safe system-parent aliases, unsafe root/child links, persistence/reopen, legacy registry, corrupt registry preservation and write failure. Include the real roster caller behavior where Android tools are available. Record observed baseline failures and final results; never substitute text search for execution. Root is preparing the Android test environment; ask root for its path, not the user. Until a command truly executes, mark it NOT_RUN.

## Delivery
Use an isolated worktree/branch in Ullmann27/device-ai, commit and push an initial checkpoint and meaningful source milestones, open a draft PR to fix/continuum-device-experience-resume-20261104. Never force-push or merge main. Write TEAM-BOTS-RESULT.md with changes, exact tests, remaining risks and next command. No additional subagents. Return status, commit SHA, PR and concise test summary to coordinator.
