# Omega memory continuation — 11 November 2026

Integration owner: root, PR #769, `integrate/omega-apk54-20261111`.
Worker: `work/omega-memory-20261111`, draft PR #770.

## Candidate and actual evidence

Continues existing Claude commits `0f2fa3f7a` and `02d9260c9` from PR #768
base `3cb8df27c42fbc6db03d05b0f9993a8650be43af`. No second database,
model promotion, tool authority, main merge, signing or installation.
Affected contracts: EXP-05 local dialogue, EXP-12 session continuity,
EXP-14 bounded context. Production integrations retain the existing transcript,
context-retention and prompt paths.

Inherited candidate first compiled with Kotlin 2.4.0/JDK 21, then JUnit 4.13.2:
**16 tests, 13 PASS, 3 FAIL**. Source checkpoint locally `b7645d32b`,
remote `1e8d68655e67f090e9e2d70ceb21fcb9cb1fb785`, identical full tree
`172a29d9641b58a0e706a566721a511e1a9eea01`.
Production dependencies came from the previously compiled source #761;
this is focused JVM evidence, not a fresh Android module build.

Failures: replacement count not bound across the negative anchor when another
object exists; the same failure after unrelated intervening turns; historical
question not recognized. New safety regressions additionally cover questions
without punctuation, hypothetical/quoted instructions, third-party objects,
unrelated values, same-noun ambiguity, unparsed changes and bounded history.
Combined negative run: **25 tests, 13 PASS, 12 FAIL** (3 inherited failures and all 9 new safety cases). Raw logs and source hashes are in `docs/evidence/omega-memory-20261111/`. The first production repair was then verified below.

## Next work

1. Record the new negative run, repair the candidate conservatively.
2. Wire model-free owner-state recall and mandatory prompt state into the real
   AgentActivity path. Root owns integration and heavy serialized Gradle QA.
3. Verify activity entry without a model and the five-step correction sequence.
4. Append source-bound results and publish each consistent checkpoint.

Fold7, physical PDF/GPU/voice/model/thermal results: NOT_RUN.
Rollback: revert this worker's isolated commits; keep original transcript and
persistent memory untouched. The ledger must never assert that an independently
stored memory does not exist.

## Repair checkpoint

- Conservative statement admission; questions without punctuation, hypothetical
  language, examples, reported/quoted text and other people's objects stay out.
- Replacement values keep their resolved negative anchor across clauses. A
  replacement with a different unit cannot change another object's count.
- Six objects, six slots per object, 24 revisions per slot, three unresolved
  notes; exceeding object/slot bounds withholds deterministic recall. A truncated
  revision history never claims its oldest surviving value was the first.
- Ambiguous same-noun objects and unparsed changes withhold old-state replies.
- Source attribution states owner conversation data, without asserting that an
  independently persisted memory is absent.
- Real Activity route after `inspectRequest`; distinct `OBJECT_STATE_RECALL`
  label; the derived state goes into required prompt data. Existing timeline
  projection is used when no object-state block exists, avoiding conflicting
  projections. This does not bypass the mandatory prompt budget.
- Explicit conversation-state follow-ups are recognized; `alles` and `danke`
  alone cannot trigger unrelated lexical/semantic retrieval.

Intermediate verification: `repair-first` **25 run / 20 PASS / 5 FAIL**, exposing
an over-conservative guard on `nein, neunzehn auch nicht`; repaired. The next
`neighbour-red` run was **76 run / 74 PASS / 2 FAIL**: all 29 ledger cases passed;
only the newly added retrieval and follow-up reproductions failed. Both are now
patched. Final focused JVM confirmation is running at this checkpoint.

`OwnerObjectStateConversationUiTest` is written, **NOT_RUN** at this checkpoint:
actual `sendButton` flow, initial statement, three recalls, two corrections,
final recall with `Runtime.EMPTY`, mandatory prompt content, source perspective,
unchanged memory records, transcript label and actual `newSession` reset.
The inference boundary is controlled; this is not a real-model/Fold7 claim.
Root schedules its serialized Gradle execution.

## Focused confirmation

Final Kotlin 2.4.0/JDK21/JUnit4 run: **78 PASS / 0 FAIL / 0 SKIP**.
Every compiled source/test SHA-256 was rechecked against source commit
`72b308ff7ac45f2efa8e942336b471e702b2074d`. Actual log and hash manifest:
`docs/evidence/omega-memory-20261111/focused-green*`.
The run includes 30 ledger cases, retention, follow-up policy/integration/frame,
and the existing hybrid retriever tests plus the new farewell regression.
This compiles the changed pure Kotlin sources against existing real compiled
app dependencies; full Android Activity compilation remains the root's gate.

Root explicitly schedules the shared Gradle worktree to avoid duplicate heavy
compilation. UI class: `at.heinz.deviceai.OwnerObjectStateConversationUiTest`
(two tests; source `app/src/uiTest/java/at/heinz/deviceai/OwnerObjectStateConversationUiTest.kt`).
Requires `-PcontinuumUiWidgetTests=true`; no custom production flags, no test
skips or graphics downgrade. Add the class to the existing selective QA scope.
UI/Android physical-device status remains **NOT_RUN** until the root's run.
