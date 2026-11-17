# Code55: connected cockpit and attention

## Scope and source

- Owner request: continue Fable's latest cockpit and intelligence work, with animation a priority.
- Integration owner: Astra. Target: `integrate/code55-apk-fable-20261112`, PR #809.
- Starting source: `779863955825b65931ef569dde79a6d2f8f8c771`.
- Working branch: `work/astra55-presence-20261113`; review and integrate into #809.
- Fable's latest source: #808 `472ef3fd6ceb137a10bbdd2090c0ae713a460bcf`.
- His reflection orientation correction is ported unchanged; earlier lighting,
  monitor glow, original assets and the source-bound host preview already exist in #809.
- Existing work gaze, eye/head springs, blink, speech gestures and bounded input
  pulses also exist. Do not implement another avatar controller or event bus.

## Current bounded step

Status: WIP / NOT_VERIFIED.

1. Preserve and verify Fable's reflection correction.
2. Make the existing work-attention path yield to owner interaction and approval;
   investigate pause/re-entry against the actual Director/reducer call chain.
3. Bind work gaze to the projected monitor geometry, including viewport cropping
   and letterboxing, using the existing scene and gaze adapter.
4. Run focused regression, source-bound visual inspection and existing Android QA.

No model training, memory promotion, new permissions, second renderer or timer.
EXP-05/06/07/11/12 contracts remain unchanged. No private owner data in tests.

## Next large milestones

| Milestone | Existing entry point | Acceptance |
| --- | --- | --- |
| Embodied work | CockpitWorkEvent → ComputerWorkAvatarBridge → AvatarDirector → renderer | Real target, speech interruption, pause, STOP and stale-event traces; Fold7 visual check |
| Natural conversation | LocalSpeechSession / ContinuousConversationMode / local context | Interrupted speech, short follow-up, return from Android consent and app resume retain the correct task |
| Grounded task continuity | LocalConversationContext / OwnerComputerWorkflow / existing memory | Outcome belongs to the exact task and observation; correction and unfinished task survive supported lifecycle |
| Cinematic finish | Existing Filament stage / packaged workstation / original avatar and five rings | Material, light, motion and sound stay synchronized; measured device frame time and heat |
| Bounded initiative | Existing orchestrator / ToolGate / owner task grant | Useful next-step proposal from a verified result; no invented completion or extra authority |

## Evidence and limits at entry

- Last source-scoped Android run on `77986395`: #43297197973 succeeded.
- Last full Android run on the same source: #43297198210 failed; investigate
  its actual failures before any full-suite success claim.
- Newly ported code and the work below are not validated by parent results.
- Fold7/GPU/thermal proof: NOT_RUN in this session.
- Historical branch catalog reviewed for relevant avatar/cockpit candidates;
  this is not a claim to have audited every historical branch.

## Resume and rollback

Check remote heads before writing. Read current changed production sources and
the test results recorded below. Revert only this step's commits for rollback;
never reset another developer's branch. Continue on the canonical #809 line.
