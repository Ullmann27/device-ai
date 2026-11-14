# Code58 support — 2026-11-14

Status: PORTIERT / HOST-VERIFIZIERT / ANDROID-QA-PENDING.
This source checkpoint precedes the combined Android run. PR #839 records its
exact-source completion receipt. No device claim.

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
   Counter-evidence selects a matching original subquestion using the existing
   lexical relevance measure and its 0.12 threshold. Unrelated contradictions do
   not invent a new counter-query; original question-gap searches remain available.
   This bounded lexical policy is not a semantic fact verifier.

## Verification and compatibility

Completed host tests use Java 21, Kotlin 2.2.0 and actual JUnit 4.13.2:

- Context: 37 tests, five baseline failures, zero after repair.
- Avatar: seven tests, four baseline failures, zero after repair.
- Research: three new baseline failures; independent review found two further
  subquestion regressions. The repaired engine and existing suites pass 23/23.
- Research and context tests compile their actual production graph with necessary
  declarations extracted verbatim from large aggregate source files. No stubs.
- actionlint and git diff --check pass.

Android SDK is unavailable locally. The existing Code58 workflow compiles full
Android sources, executes all affected JUnit/Robolectric suites, requires the new
named cases, and then builds an unsigned ARM64 candidate. This is the same build
path as #836; no duplicate release lane. The reviewed source is stored in #839.
Physical Fold7, live capture, real model quality and measured animation FPS remain
NOT_RUN unless separately observed.

Relevant register: EXP-06 (source provenance), EXP-07 (regressions), EXP-12 (continuity),
EXP-14/EXP-16 (context). Existing cinema flag: EXP_09_FOCUS_BLINK. No new database, renderer,
permission, source of action authority, model weight or signing mechanism.

Each worker's commit is reviewed and integrated here before the common QA.
Rollback is a revert of the bounded support commits; the parent remains reachable.

Research source SHA-256:
`54128187b48d6f803990d3f97fc4c1915573ee0eecda8b9621282bb1219887fa`.
Research regression SHA-256:
`343d7fe2fce2618050bfe1361a2dac61ead7d65575792e4b6945d764bad11814`.
