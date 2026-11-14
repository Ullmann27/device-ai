# Code58 support — 2026-11-14

Status: WIP / UNVERIFIED. No device or APK claim.

## Integration

- Canonical parent: PR #836, `fix/code57-fold7-downloads-research-20261114`.
- Verified parent: `1656443d137bb6787c2eabe69a3e91ff1b2275b2`.
- Team integration: `work/code58-three-agent-support-20261114`.
- Integration owner: root; three isolated context, avatar and display workers.
- Parent QA 43537197739 completed successfully; it does not verify these changes.

## Bounded repairs

1. Context: reproduce polite pronoun follow-ups losing the existing task frame;
   preserve standalone questions, new topics and action boundaries.
2. Avatar: reproduce the 460-second animation clock wrap suppressing gaze blinks;
   preserve normal cooldown and existing rendering effects.
3. Display: reproduce a failed frame-driven VirtualDisplay surface replacement;
   reuse the existing failure/cleanup path and reject stale callbacks.
4. Integration review: verify PR #836's title filter against composed counter-evidence
   queries and untrusted follow-up text; keep the owner's research topic authoritative.

## Verification and compatibility

Use targeted Kotlin/JUnit and Android/Robolectric regressions on the combined source.
Local Java 21 and Kotlin compiler are present. Android SDK availability is not yet
established. Existing Code58 QA is the intended build path; no duplicate release lane.
Physical Fold7, live capture, real model quality and measured animation FPS remain
NOT_RUN unless separately observed.

Relevant register: EXP-06 (source provenance), EXP-07 (regressions), EXP-12 (continuity),
EXP-14/EXP-16 (context). Existing cinema flag: EXP_09_FOCUS_BLINK. No new database, renderer,
permission, source of action authority, model weight or signing mechanism.

Each worker's commit is reviewed and integrated here before the common QA.
Rollback is a revert of the bounded support commits; the parent remains reachable.
