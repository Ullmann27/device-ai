# Grok Bot architecture study — reusable ideas for Device AI Continuum

Date: 2026-10-16. Scope: public documentation and independently written references.
Grok Bot is a **Cursor** product. It is not OpenClaw, and the similarly named
Grok LLM/other GrokBot GitHub repositories are not the product's source tree.

## Public primary sources

- https://prod.cursor.com/docs/grok-bot — overview of persistent AI teammates,
  one shared cloud computer, coordinated bots, demonstrable routines and memory.
- https://prod.cursor.com/docs/grok-bot/work — per-bot roles, persistent context,
  handoffs, tested reusable skills and scheduled workflows.
- https://prod.cursor.com/docs/grok-bot/security — isolation, explicit authority,
  separate local execution approval, token handling, remote cloud dependency.
- https://prod.cursor.com/docs/grok-bot/teams — agent computer infrastructure,
  permissions and separate review controls.

These documents describe product behavior. The proprietary implementation is
not distributed as a generally reusable open-source agent library. Third-party
reverse-engineering articles and cloned repositories may inform design reviews,
but no proprietary source, assets, protocols or credentials are imported.
Review every candidate dependency's license before use.

## What is worth adapting (clean-room, independent implementation)

| Grok Bot principle | Continuum implementation / next acceptance |
| --- | --- |
| Persistent named coworker and role context | ONE Teamleader identity and checked development backlog; persist exact outcome, model provenance and task ownership. Not 100 nominal agents. |
| Shared computer and handoff across bots | Android owns owner experience/permissions now; Node worker on optional user-controlled PC runs git/worktrees. Use typed export/import of canonical backlog plus exactly-once op IDs; no cloud machine dependency. |
| Save successful workflow as a skill, then schedule | Use an **allowlisted** versioned routine spec: task kind, required capability, deterministic steps, verifiers, timeout, last outcome and rollback. Only schedule after at least one real tested execution. |
| Schedule with failure transparency | Android JobScheduler for bounded health and task audits; local systemd timer for Git/Node. Interrupted jobs are not DONE. No guarantee Android runs precisely hourly or while powered off. |
| Approval and computer-use boundaries | Existing ToolGate, owner-confirmed knowledge, isolated git worktrees, no direct production-branch writes. Preserve strict separation between untrusted fetched content and execution instructions. |
| Memory persists across sessions | Existing SWB1/CRE1 memory/experience/Replay layer; only verified observations and user-confirmed facts may train/promote. |
| Agent can operate websites/docs | Future safe browser/document tools require authorization, provenance, bounded extraction, injection resistance, and actual cited sources. |

## What we explicitly DO NOT copy

- Cursor's paid, hosted always-on microVM, account subscriptions or model APIs.
- The hosted cloud control plane, proprietary app bundle and remote credentials.
- Product claims about unlimited autonomy or 24/7 service on powered-off
  personal hardware. A device must remain on, with a running eligible worker.
- Electron/macOS tooling in the Android APK.
- Third-party clean-room code with an incompatible copyleft license without a
  deliberate project licensing decision.

## Android-first present reality

1. DeviceTeamleader.kt and ContinuumDeviceTeamleaderScheduler.kt are committed
   as an Android-side supervised health/backlog runtime. They require a full
   Android build, JVM tests and on-device scheduling test for verification.
2. Autonomous Teamleader Node code already coordinates repository analysis,
   bounded actual tests, isolated repair candidates and read-only investigation.
   A scheduled local desktop user service is optional, not an API requirement.
3. DeviceDevelopmentBacklog already supports canonical backlog export/import
   and an idempotent operation log. This is the clean, portable handoff basis.
   Actual authenticated two-way Android-PC synchronization is **NOT wired**.
4. The first shipping target is: launch Android -> show agent state -> request
   one bounded diagnostic -> record a verified result -> resume after app
   restart. On a future PC, the same backlog must move an eligible task through
   a real repository checkout/test/patch-review cycle.

**Suggested next feature inspired by Grok Bot:** "Teach a routine" in Continuum
should NOT record arbitrary taps or blindly execute replayed scripts. First
record only a successfully tested, typed, narrow workflow and its required
verification and permissions; add optional on-device demonstrations later.
