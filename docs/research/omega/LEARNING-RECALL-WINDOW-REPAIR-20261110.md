# One bounded recall repair

Initial source `ad995a37463b110760e880a74db7cf1d806dd492` is
REJECT_PROMOTION. Independent evaluation exposed a real regression: 64 future
records displaced an eligible record after recency became the first tie-break.
The original source, 28/28 development result and independent rejection remain
preserved. Development green did not establish independent acceptance.

The integrator authorized one repair round: filter the inclusive completion
window before ranking and capping in the existing in-memory lookup. The old
three-argument lookup remains available. Recall supplies its explicit clock;
there is no hidden clock, migration, new database, model call or authority change.

Before repair, the unchanged initial source with two additional directed tests
runs 30 tests with 2 failures. They reproduce future-data displacement and
displacement by higher-relevance expired records. Raw logs and hashes are in
`docs/evidence/learning-recall-recency-20261110/repair-red`.

The additive `ExperienceLookup.similarWithin` default filters the provider's
existing bounded shortlist. It cannot recover eligible records already excluded
by a legacy provider. Only the in-memory override enforces the window before
ranking and cap. No unbounded overscan is permitted. Recall retains its defensive
time and outcome checks. Evidence/outcome filtering remains after the candidate
cap, so time-eligible records without sufficient evidence can still occupy it.

Signature: `similarWithin(taskType, request, completedAtFrom, completedAtThrough,
limit = 5)`. Bounds must satisfy `0 <= from <= through`, and limit must be
nonnegative. Both endpoints are included. Zero limit returns without invoking a
legacy provider. Existing `similar(taskType, request, limit)` uses the unrestricted
completion window `0..Long.MAX_VALUE`; relevance, recency and ID order are retained.

Development result after this repair: 36/36 host tests pass. This comprises 27
unchanged prior tests, one explicitly revised legacy lookup control, the two
directed RED regressions, and six additional window/provider contract tests.
The revised control now checks that the unchanged unrestricted lookup still
selects higher-relevance expired records. Its former empty-recall expectation
is intentionally superseded by the separate failing-then-passing window test.
The 30-test RED batch and 36-test GREEN batch are not identical test populations;
do not report an independent 30/36 quality comparison.

Checks cover inclusive 30-day/now boundaries and adjacent excluded milliseconds;
explicit caller clocks at epoch, below 30 days and Long.MAX_VALUE; relevance,
recency, stable ID order and cap; invalid windows, negative and zero limits;
conservative legacy fallback; and recall rejecting out-of-window responses even
when a provider violates the new window contract. Existing permission and
unverified-hint tests also pass. No source text from old tasks becomes authority.

GREEN evidence is in `docs/evidence/learning-recall-recency-20261110/repair-green`.
It records RED checkpoint `b66175dce25314be84e540686ead58dddc2d97a8`, the tested
uncommitted production patch, and source hashes. Only trailing blank log lines
were normalized. Reproduction uses the existing
`scripts/research/run-experience-recall-recency.sh` and frozen compiled dependency
snapshot described in the original handoff; it is not a full Android build.

Next: one rerun of the now-exposed independent regressions and source-bound full
Android compilation. This is not a fresh holdout or independent intelligence
acceptance. If further defects remain, stop with REJECT_PROMOTION. No autonomous
skill promotion was added. Fold7, model quality, thermal and energy tests: NOT_RUN.
Rollback: revert this additive method, in-memory window implementation and recall
call together. Do not promote the rejected comparator-only predecessor.
