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

The additive `ExperienceLookup.similarWithin` default will filter the provider's
existing bounded shortlist. It cannot recover eligible records already excluded
by a legacy provider. Only the in-memory override will enforce the window before
ranking and cap. No unbounded overscan is permitted. Recall retains its defensive
time and outcome checks. Evidence/outcome filtering remains after the candidate
cap, so time-eligible records without sufficient evidence can still occupy it.

Next: implement this single repair, test bounds and legacy fallback, then freeze
source for one rerun of the now-exposed independent regressions. This is not a
fresh holdout or independent intelligence acceptance. If further defects remain,
stop with REJECT_PROMOTION. Fold7: NOT_RUN.
