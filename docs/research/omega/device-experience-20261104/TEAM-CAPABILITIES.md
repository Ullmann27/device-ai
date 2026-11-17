# Team 3 — honest capabilities and speech feedback

Status: WIP / UNVERIFIED at assignment. Base source: 337ab0195434c8657bf131bf431f4c391aacc28c (PR477).

## Requirements
Read AGENTS.md, docs/CONTINUUM-EXPERIMENTVERTRAG.md, relevant EXP-05/11/12 entries and PLAN.md global constraints. Replace the static owner-facing self-description with an honest read-only capability snapshot built from the real current imported/loaded model, canonical dashboard/worker/bot and permission state. A capability question must never start inference, grant tools, clear barriers, leak bot-private context or claim host evidence as phone proof. Preserve conversational intent routing and ordinary messages.

Distinguish speech no-match/no-input, microphone permission, recognition service/language availability, cancellation and actual failure. Use actionable German messages; stale callbacks and retries must not revive a cancelled or replaced turn. Inspect the existing AndroidOnDeviceSpeechInput and LocalSpeechInputPolicy before modifying.

## Ownership
Own capability adapter/policy classes, AndroidOnDeviceSpeechInput.kt, cognitive/LocalSpeechInputPolicy.kt and directly associated tests. Do not edit ui cockpit, avatar, ChiefBotProfileStore or ContinuumBotRosterDialog. AgentActivity.kt is coordinator-owned: provide exact bounded insertion/replacement patch and tests for coordinator integration. No model/service launch or S01 barrier change.

## Acceptance
Meaningful tests cover ready/unavailable/unknown state, ordinary-input routing, privacy boundaries, no side effects when answering status, no-match vs permission/language errors, cancellation, stale callback exclusion and lifecycle release. Root prepares Android build tools. Report NOT_RUN until truly executed, keep original failing evidence and final observed outcomes.

## Delivery
Use an isolated worktree/branch in Ullmann27/device-ai, initial checkpoint and draft PR to fix/continuum-device-experience-resume-20261104, then push each meaningful source milestone. No force-push or main merge. Write TEAM-CAPABILITIES-RESULT.md with source interfaces, precise coordinator wiring, commands, results and remaining risks. No additional subagents. Return status, commits, PR and concise test summary.
