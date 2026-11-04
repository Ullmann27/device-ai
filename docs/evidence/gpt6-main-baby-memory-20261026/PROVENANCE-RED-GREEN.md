# Baby Core provenance: independently reproduced RED/GREEN (26 October 2026)

## Source provenance
- Production target: GitHub-side Baby Core **not** Android ContinuumMemoryDialogue or owner's installed APK.
- RED original main SHA `190f5eabaa60fc59292b2b80d3086aafd7743a80`, exact `.github/continuum/baby-core.mjs` Git blob `f10c8ed8a2a401fa7c7f4e8cb20c415c04978683`.
- GREEN review branch `review/continuum-baby-memory-migration-20261026`, patched source blob `df049d3f2faac38b7fb263a64ba89a51d22cf5fd`; source commit `54cae7d36605753e8b860dcb8931840fcc5c55ed`.
- Added tests to `.github/continuum/baby-core.test.mjs` in commits `802dd64e825ee6c9ae9dbd66d511b5e8e2ed0e84` and `5b59662e474c11958f61d9aed19a433dde57532c`.
- Existing v1 migration test's schema and fixture were updated in `2c2e38b44348e5b84c10491896a37aa076eff8b4` and `838705fcc56fbbcbbdce71614a78ae15a0703a6f`.

## Reproduction 1: duplicate consolidation
Used original source functions `migrateBabyState`, `consolidateBeliefs`, `sanitizeBeliefText` extracted unchanged from the two pinned GitHub blobs into an isolated V8 evaluator. Test duplicates had exact same normalized text; provisional source confidence `0.94`, citation `unverified.example`, createdAt `100`; grounded source confidence `0.73`, citation `publisher.example`, createdAt `120`.

| Result | Old main | Patched branch |
|---|---|---|
| Selected ID | `provisional-high` | `grounded-verified` |
| Status | GROUNDED_OBSERVATION | GROUNDED_OBSERVATION |
| Selected confidence | 0.94 (from provisional) | 0.73 (from grounded record) |
| Citation list | Provisional + grounded | Grounded only |
| Creation date | 100 (provisional) | 120 (grounded) |

This verified the old code's cross-tier status escalation and the bounded correction using the exact source functions. No owner data was read; fixtures were synthetic.

## Reproduction 2: repeated evidence intake
The actual `ingestEvidence`, `migrateBabyState`, `addGoal` and required local helpers were source-extracted unchanged from the same pinned blobs. Same-claim weak → stronger → weak ingest, with first weak record confidence `0.35` and stronger record confidence `0.823`:

| Stage | Old main | Patched branch |
|---|---|---|
| After weak | PROVISIONAL, 1 weak URL | same |
| After stronger | GROUNDED, **4** URLs (old weak included) | GROUNDED, **3** verified URLs only |
| After final weak | Last validated `300`, 4 URLs | Last validated `200`, 3 verified URLs |
| V1 → V3 meaningful legacy sample | 1 PROVISIONAL, confidence `0.55` | same |

Other changed test expectation: the live migrator actually upgrades v1 → v2 → v3 in one call. The old test incorrectly asserted `schemaVersion=2` and its short synthetic sentence was dropped by the current navigation/noise sanitizer. The test fixture now uses a meaningful complete claim and asserts v3, its `memoryRewardVersion=2`, and confidence downgrade.

## Execution boundary (must not overclaim)
This was **real execution of the exact extracted JavaScript source functions** in a fresh JavaScript V8 isolate, but **not** a complete Node 22 module import or Node `--test` run. It supplied a JSON round-trip `structuredClone` equivalent, `SKILLS` for the used behavior and a deterministic **test-only hash shim** in place of Node crypto for same-claim identity. These substitutions were outside the actual retrieved function bodies and do not authenticate production artifacts. This proves the reproduced behavior for these synthetic inputs, not general correctness, full unit suite or Android device quality. The new checked-in Node test cases are `COMMITTED / NOT_RUN_FULL_NODE`. No Android SDK, JUnit, Fold7, real neural inference or APK tests were run for this branch.

## Remaining validation
From a full authorized repository checkout, run:
```sh
node --test .github/continuum/baby-core.test.mjs
node --test .github/continuum/*.test.mjs
```
Then review source-level effect on existing `continuum-state` schema-v3 migration in read-only shadow/backup; no live state migration before acceptance. Keep the branch Draft; no auto-merges into main, owner-data writes, signed APK or state reprocessing. Rollback only this review branch's source/test commits. EXP-06, EXP-07, EXP-12.
