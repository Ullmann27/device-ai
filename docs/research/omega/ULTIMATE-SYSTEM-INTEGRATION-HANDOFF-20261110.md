# Ultimate system integration — 10 November 2026

Status: WIP / UNVERIFIED combined source. Owner: ultimate integration root.
Canonical branch: `integration/continuum-ultimate-system-20261110`.
Initial Cinema source: `c972f56ccc4caa2f1807bcaf1d4665347c66c629` / PR #715.

## Scope

Combine reviewed existing Cinema, dialog, outcome evidence, memory and Android
system paths. Reuse AgentActivity, LocalConversationContext, cognitive runtime,
ToolGate, DeviceToolExecutor, CockpitAnimationBus and shared Cinema time.
No second state store, event bus or inference engine.

First audit covers PRs #715–#737 and their existing integration ancestors.
Historical catalog remains `docs/integration/PR-BESTAND-20261109.jsonl`;
catalog presence is not proof of a complete source audit.

## Boundaries

OMEGA #721 is REJECT_PROMOTION and must remain inactive. Experimental #728
weights cannot replace the local model. Research #717–#719 stays opt-in/off.
No main merge, original signing, installation, release or data migration.
Candidate versionCode must exceed 52 and retain the original package.
Physical Fold7, real Gemma, live microphone/TTS, GPU/thermal: NOT_RUN.

## Sequence and checks

1. Freeze donor SHAs and review findings; preserve upstream worktrees and PRs.
2. Incorporate existing verified dialog/outcome/Cinema fixes.
3. Review scoped memory repair and device diagnosis integration; fix real gaps.
4. Run serial exact-source Android compilation, JVM/Robolectric, Node and lint.
5. Build an unsigned candidate only after successful combined QA.

Use existing bounded build procedures and resource lease. Report failures and
test scope separately. Checkpoint each coherent step immediately.
Rollback uses reviewable revert commits on this integration branch; no resets,
force-pushes or branch deletion.

Applicable existing experiments: EXP-05 local inference, EXP-06 provenance,
EXP-08 job/result identity, EXP-11 existing orchestration, EXP-12 continuity.
No new research experiment is opened.
