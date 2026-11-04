# Memory, brain and avatar integration — 2026-11-04

## Owner request and exact baseline

Heinz assigned this workstream memory, retrieval and the remaining brain logic,
including research, experiments and tests. Two other conversations own model
weight expansion and graphics/avatar/design. The owner explicitly requires a
connected input → memory/brain → answer → speech/visible-avatar flow, with an APK
built only after the combined work is finished and verified.

Base: PR #483, `578bdfd12f896cc990bf1a043bc1b248ba3adb6d`.
Branch: `feat/continuum-memory-brain-20261104`.
Initial status: **WIP / implementation NOT_RUN / APK NOT_RUN**.
Inherited test counts are not results of this workstream.

## Coordination and ownership

- #492 / `research/continuum-weight-space-audit-20261104` owns weight edits,
  parameter widening, model training, candidates and weight-space experiments.
- #491 / `codex/astra-jarvis-takeover-20261104` owns the current visual integration,
  AgentActivity, cockpit/head/orb views, voice/animation presentation and the
  existing M2.10 recovery work in ContinuumLearningCore/LearningExperienceStore.
- This branch owns focused cognitive retrieval/evidence/reasoning corrections,
  their tests, a repeatable memory challenge and integration checks of the real
  memory-event contracts. Exact additional production paths will be recorded
  before edits; shared Activity/UI/recovery/weight paths are not overwritten.
- Existing `ContinuumMemoryEvents`, `MemoryEventSource`, cockpit bus, avatar
  director, voice session, `AiContextCompiler` and `BoundedAgentOrchestrator` are
  the interfaces to reuse. Do not create a second memory database, orchestrator,
  avatar state machine or simulated event source.
- Read current PR heads, changed files and handoffs again before combined
  integration. A posted coordination note is not evidence that another worker
  acknowledged it or finished its work.

## Required integration behavior

1. Recall respects provenance, privacy, owner corrections and evidence absence;
   stale/revoked/held memories do not become confident current facts.
2. Real recall/storage events reach the visible avatar/orb through the existing
   listener and main-thread mapping contracts, carrying IDs/hashes rather than
   private memory text. No animation reports an operation that did not occur.
3. Speech/listening/cancellation states refer to the same active conversation;
   closing or replacing a screen/session cannot deliver stale events to it.
4. Counters and learning outcomes distinguish retrieval, a proposed answer and
   an independently checked real-world result.
5. Validate the combined source with focused RED→GREEN regressions, relevant
   unchanged suites, Android compilation/lint and a source-bound APK build.
   Host tests remain distinct from Fold7/audio/GPU acceptance.

## Execution and constraints

Use the existing Kotlin/Android stack and deterministic local tests. Research
uses primary sources and synthetic non-owner fixtures. EXP-06 (provenance),
EXP-07 (independent challenges), EXP-11 (one orchestrator) and EXP-12 (continuity)
apply; the current experiment contract and issue #41 were read.

No main merge, force-push, paid compute/API, productive weight promotion,
permission expansion, secret export or device installation. Preserve original
package `at.heinz.deviceai.continuumlocal`, signing identity and update channel.
Do not change version/signing/publisher while other workstreams are unfinished.
Recovery consent semantics remain the explicit-owner-confirmation contract.

## Next entry

Complete the independent source audits; record bounded file ownership and the
test plan; reproduce defects before changing production code. Coordinate heavy
Gradle work with the existing shared builder lock and preserve raw logs with
source hashes. Publish every meaningful checkpoint to this same official repo.

## Visibility and publishing authorization

The first push was rejected by automatic review while historical instructions
said private and live metadata said public. The owner explicitly confirmed on
2026-11-04 that they made this repository public for collaborating KIs and
authorized publishing the requested project changes and coordination. This
new consent resolves that specific block; it does not authorize publishing
private source data, credentials or personal model weights. AGENTS.md is
corrected accordingly. No repository setting is changed by this workstream.
