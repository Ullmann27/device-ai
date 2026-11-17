# CCC1 independent proof: executable test identity parity — 2026-10-26

**Status:** REVIEW ONLY. Dedicated stacked Draft PR #164 on PR #163; no model promotion, owner memory, repair-ledger mutation or main merge.

## Problem reproduced

The original `repair-loop.mjs:evidenceSufficient` permits successful candidate results when candidate executed-test count matches *baseline passing* count, which can be below baseline total executed count. For example baseline `executed=120,failed=1,passed=119` versus GREEN `executed=119,failed=0` looks successful despite one lost testcase. The first change in this PR forced total executed count parity.

A second gap remained: **the same count does not prove the same tests ran**. A red test can disappear while an unrelated benign test appears; a quantity-only check still cannot establish causally repaired behavior.

## Actual code changes

- `.github/continuum/repair-diagnostics.mjs`: attach hashed `executedCaseHashes` to parsed JUnit results, derived from `classname + '\\0' + name` for non-placeholder, non-skipped cases; a missing class/name results in a `null` entry. Aggregate across `readJUnitDirectory` without disclosing raw testcase names.
- `.github/continuum/repair-loop.mjs`: keep a sorted, collision-rejecting `tests.executedCaseHashes` list only when **every** executed case had exactly one distinct valid SHA-256 identity; otherwise no identity evidence is published (later gate fails closed).
- `.github/continuum/ccc1-independent-verifier.mjs`: `assertCcc1TestEvidenceParity` requires baseline identities to be a subset of GREEN identities, with no duplicates/missing identities, no total executed-test drop, and stable skipped test identities. This is applied RED→primary GREEN, RED→fresh GREEN, and primary→fresh GREEN. A broader green run may add new tests but cannot silently remove earlier tests.
- `.github/continuum/ccc1-independent-verifier.test.mjs`: focused negative cases for counts, substitution, skip identity changes, duplicate/missing or inconsistent result data.

## Independently executed checks

The source-bodies of **the exact GitHub blobs** for the parity helper and its appended test callbacks were fetched and run in an isolated V8 runtime, with no worktree execution. **6/6 passed**:
1. RED 120/failed 1 → GREEN 119/failed 0: reject.
2. Equal or expanded executed count: accept.
3. Different skipped testcase hashes or skip counts: reject.
4. Absent profile, zero executed tests or contradictory counts: reject.
5. Same executed-test count with a swapped testcase hash: reject.
6. Missing, duplicate or malformed executed-test identities: reject.

The actual `parseJUnit` source body was separately executed on sample synthetic JUnit XML in V8: two valid executed case identities and one skipped case were distinguished correctly, and replacing a testcase name changed the hash collection. Its test-only hash shim is NOT cryptographic; production code uses the real `sha256` helper.

**NOT RUN:** native full `node --test` on exact published module graph, actual Docker-isolated profiles, Android or Fold7. No real RED→GREEN→fresh GREEN verification has been claimed. Sol's native numeric tests mentioned in screenshots were performed in Sol's separate session, not independently attested on this PR.

## Known limitations and risk tradeoffs

- A testcase identity hash attests only the label reported by the runner, not the actual test implementation or whether an attacker fabricated XML; pinned test-file digests, owner-bound test manifest and the OS isolated executor remain separate mandatory gates.
- Node JUnit often uses a generic `classname='test'`. Duplicate testcase names are possible; under this strict research verifier these cases deliberately fail closed instead of being accepted as independent proof. To unlock real CCC1 jobs when duplicates occur, develop stable disambiguation from the canonical fixed runner (e.g. per test file/suite path) and independently bind runner version/manifest, rather than weakening the proof silently.
- If production code dynamically registers extra tests only when a bug is fixed, the GREEN identity list may be larger. The subset rule permits additions while preventing disappearance of baseline cases.
- The parser currently uses regular expressions for JUnit XML and only hashes names/classnames, not a signed external attestation. Validation of adversarial XML and untrusted reports needs separate review.
- No data from model output, untrusted logs or a supplied SHA string is treated as sufficient to change model weights.

## Next owner-authorized gate

Run `node --test .github/continuum/ccc1-independent-verifier.test.mjs` and `node --test .github/continuum/repair-diagnostics.test.mjs` against the **exact HEAD** in a complete trusted checkout. Then use a real prepared owner task + branded OS-isolated executor to measure all three runs; do not fall back to `node:vm` or ordinary `spawnSync`. Review the resulting ledger only after complete test identity and manifest integrity are independently observed.
