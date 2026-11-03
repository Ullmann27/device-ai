# Independent persisted-proof review

Production change: 6baec1bcd6585d2d04688a7d15651c830b4886d7.
Baseline regression commit: 844f94fd, prior to the fix.
Command on separately extracted exact sources:
`node --test --test-reporter=tap .github/continuum/ccc1-technical-recall.test.mjs .github/continuum/ccc1-experience-durability.test.mjs`

Baseline: 51 tests, 50 pass, 1 fail, 0 skipped, exit 1.
Candidate: 51 tests, 51 pass, 0 fail, 0 skipped, exit 0.
The failing assertion is the persisted proof MAC after JSON serialization.

The narrow change validates the original private branded live objects, creates the
JSON-persistable snapshot, validates bindings again, then signs exactly that snapshot.
It does not re-sign old damaged history or weaken runtime/verifier admission.
The regression uses explicit synthetic WeakSet fixtures for inaccessible live brands;
these 51 tests are contract evidence, not neural inference. Genuine new-write and
restart behavior will be measured separately in the prospective three-task A0.
Recommendation: accept this narrow fix plus its regression before new model tests.
Chief integrated exact helper/regression into fd743adce297c0958aadce1576b80580817837f0.
