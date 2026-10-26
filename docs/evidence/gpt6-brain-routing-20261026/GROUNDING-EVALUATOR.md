# Frozen 60-case independent grounding replay — prepared 2026-10-26

Status: **RUNNER_COMMITTED / 60_CASE_COMPARISON_NOT_RUN** on this branch.
The fixture was independently authored and frozen before SOL PR153 implementation was inspected. It is already committed in the parent PR151 and is never rewritten or used for training.

### Immutable evaluation inputs
- Baseline code: `c391b83244bd2cc945a1b37bcdd829da27e15ba6:.github/continuum/dialogue-retrieval.mjs`
- Candidate code: `4bf74b1b827aab56bbe03e032c21c03c508484c8:.github/continuum/dialogue-retrieval.mjs`
- Exact frozen 60-case JSON in `docs/evidence/sol-breakthroughs-20261026/grounding-independent-not-run/grounding-decision-fixtures-v1.json`
- Fixture SHA-256: `6a1a414786b22eec0573e25dd7f8aaf1972f5f2a5916181173fc612265672c12`
- 48 in-scope cases and 12 challenge controls. No owner data or model inference.

### Reproduction
Run from an authorized **full Git checkout** containing both pinned Git objects, using Node 20+:

```sh
node --test scripts/continuum-grounding-independent-eval.test.mjs
node scripts/continuum-grounding-independent-eval.mjs --output /tmp/continuum-grounding-independent-60-new.json
```

The evaluator:
1. Checks SHA256 and exactly 60 unique fixture case IDs.
2. Reads immutable baseline/candidate JavaScript with `git show <sha>:<path>`, without changing branches or any Git index/ref.
3. Replays each memory test with `rankMemories(query, memories, { semantic: null, limit: memories.length })`.
4. Scores sorted exact hit-ID sets; records missed positives, false hits, errors, all raw score/evidence objects and separate `sourceTermEvidence` diagnostics.
5. Breaks out `in_scope` and `challenge`, kind and category, and baseline-true-positive retention. Exceptions never become success for cases expecting no matches.
6. Writes the raw report **only to a new, caller-named output path** (`wx` prevents overwriting previous reports).

No success metrics for the frozen 60 cases are yet asserted. The optional summary unit fixture checks are committed but the exact on-repository test script has **NOT_RUN** within this connector-only runtime. A local Node22 prototype of the same scorer design passed three unit checks; do not confuse that with the full pinned-source evaluation or with Android quality.

### Next verification/integration gate
Run the two commands above on a full authorized clone; save raw report, execution environment and SHA of this PR to the same branch in a new evidence checkpoint. Compare baseline and candidate outcomes; if any holdout case is unsatisfactory, create **new** exploratory tests/split for further changes and never retune against the consumed 60-case holdout. Then review Android source and Kotlin parity before selectively integrating PR153 into PR151. No main merge, no owner-memory writes, no APK release.
