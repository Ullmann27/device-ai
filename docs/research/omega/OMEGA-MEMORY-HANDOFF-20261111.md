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
Combined negative run: **25 tests, 13 PASS, 12 FAIL** (3 inherited failures and all 9 new safety cases). Raw logs and source hashes are in `docs/evidence/omega-memory-20261111/`. No production repair yet.

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
