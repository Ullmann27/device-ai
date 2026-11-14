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
   Counter-evidence first selects original subquestions with the existing
   substantive topic anchors; lexical overlap only ranks matching questions.
   Long relevant claims and questions retain counter-evidence searches without
   a length-dependent admission threshold. Unrelated contradictions do not invent
   a new counter-query; original question-gap searches remain available.
   This bounded lexical policy is not a semantic fact verifier.

## Verification and compatibility

Completed host tests use Java 21, Kotlin 2.2.0 and actual JUnit 4.13.2:

- Context: 37 tests, five baseline failures, zero after repair.
  A concurrent continuation addition extends the same suite to 40 tests; its
  single reproduced test-contract error is repaired through the actual history
  path below. Final host result: 40/40.
- Avatar: seven tests, four baseline failures, zero after repair.
- Research: three new baseline failures; independent review found two further
  subquestion regressions and two length regressions. The repaired engine and
  existing suites pass 26/26, including an off-topic single-question control.
- Research and context tests compile their actual production graph with necessary
  declarations extracted verbatim from large aggregate source files. No stubs.
- actionlint and git diff --check pass.

Android SDK is unavailable locally. The existing Code58 workflow compiles full
Android sources, executes all affected JUnit/Robolectric suites, requires the new
named cases, and then builds an unsigned ARM64 candidate. This is the same build
path as #836; no duplicate release lane. The reviewed source is stored in #839.
Run 43566865117 verified 181/181 Android/Robolectric cases on c6ca29c92f34a6204d9151f08ab8e4f783663558,
including all new display cases. The subsequent length fix adds three tests;
its final combined-source Android/build receipt must be read from PR #839.
Physical Fold7, live capture, real model quality and measured animation FPS remain
NOT_RUN unless separately observed.

Relevant register: EXP-06 (source provenance), EXP-07 (regressions), EXP-12 (continuity),
EXP-14/EXP-16 (context). Existing cinema flag: EXP_09_FOCUS_BLINK. No new database, renderer,
permission, source of action authority, model weight or signing mechanism.

Each worker's commit is reviewed and integrated here before the common QA.
Rollback is a revert of the bounded support commits; the parent remains reachable.

Research source SHA-256:
`33d28a03ff5ff3303bb80eae47b356a965c3210c264fd9b053af01fd0a7b54b8`.
Research regression SHA-256:
`343d7fe2fce2618050bfe1361a2dac61ead7d65575792e4b6945d764bad11814`.
Research length regression SHA-256:
`513be6df66e55875c7ccc3d3d5cec70ca05205b34c5019b072977d21d9412f04`.

## Concurrent continuation follow-up

The shared branch advanced to `5c1a14e53e9b88b394f47aa56554aa11e2441005`, preserving
the complete support changes and adding bounded repeated-letter recognition
for owner continuation requests. This addition is retained unchanged.

Its original new test expected a `LocalFollowUpTaskFrame`, although native
continuations deliberately bypass that frame. Exact-source host execution
reproduced 40 tests / 1 failure. The repaired test exercises the actual
`LocalDialoguePromptHistory` path used through `LocalConversationContext` by
`AgentActivity`: complete chronological owner turns, the last answer's real
ending under a 1200-byte budget, unchanged current request, no false history
deduplication, and rejection of missing answer-end or incomplete owner data.
The continuation regex, frame contract and production pipeline are unchanged.
The original 100 spelling variants and 20 negative controls remain present.
Final host run: 40/40; these are JUnit methods, not the variant count.

The preceding `2c1a81c6abddbf3bc1ff442eed56776a6f1860b1` run 43567432907 passed
184/184 selected Android/Robolectric tests and the unsigned ARM64 build. It does
not verify this subsequent addition. The existing workflow now requires all
three continuation methods; PR #839 records the combined 187-case run and build.

Final context regression SHA-256:
`5fe1156adb77a076bbc5dad4a7a482932454b69a9511b40590e681053c0f9bd2`.
