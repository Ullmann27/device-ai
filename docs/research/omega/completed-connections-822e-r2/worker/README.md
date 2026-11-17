# Completed connection: canonical backlog selection

Status: **local Node validation PASS; pending parent integration/publication**.
Scope: the retained R1 worker selection from #511, on the frozen C1 input.

## Source and bounded change

- Frozen local input: `3510c26110098b42d46bb3b7788027fa7aed59e4`.
- Published C1 parent: #519, `1a5302c504a78a50d5e64ca727f2915529bf2b06`.
- Candidate: #511, `73b64ec299fd314dfdf07835fc7d8fc0d74b51a2`;
  available local equivalent: `2f20c19b7e37197a6accac0c226b5401e25f1f99`.
- GitHub connector verification of the pinned production source returned blob
  `dfa1f4b762a9e45c81d47b3431ca35d013d85d9c`. The resulting **whole**
  `.github/continuum/development-backlog.mjs` has that exact Git blob.
- Baseline source blob: `64f798d5cf86ec7b541bd5135972f30fef1be746`.
- Production diff: only `nextEligible`, 11 added / 6 removed lines. It keeps one
  best eligible task instead of allocating and sorting all eligible candidates.
  Priority descending, creation time ascending, ID ascending, optional scopes,
  filter evaluation order and returned object identity remain the same.
- Imported the four existing R1 regression tests into
  `.github/continuum/development-backlog.test.mjs`; the complete resulting test
  file matches upstream blob `b1ea996a9aed21ef5dccca67dcab16e481314f25`.
  Coverage includes ordering, filters, short circuiting, attempt budgets and
  dependency completion evidence. Existing tests are retained.
- The small `tools/completed-connections-822e-r2/worker/selection-parity.mjs`
  harness retains R1 fixture generation and parity cases, binds the reference to
  the C1 source blob, and omits all timing code. It requires the baseline Git blob
  to be present in repository history. Blob binding survives differing local and
  published commit IDs. The initial commit-bound run passed; the final blob-bound
  harness was rerun and its output is retained here.

No archived R2 dependency-search implementation or harness is imported.
Eligibility, document validation, claim/claimNext, budget transitions, dependency
lookups, persistence, locks, approval gates and existing C1 ECHO code are unchanged.

## Existing execution connection

`teamleader-cli.mjs` passes `runTeamleaderCycle` as the work callback to the
existing `runTeamleaderLoop`. The local driver in `autonomous-teamleader.mjs`
reads the canonical `BacklogStore` and calls `nextEligible` for owner REPAIR,
owner TEST / ASSERT_NODE_EXPORT and INVESTIGATE tasks. The existing
`backlog-repair-worker.mjs`, `owner-contract-worker.mjs` and
`backlog-investigator.mjs` also select from this same export. No caller change or
second scheduler is needed. Existing immutable-head, acceptance and evidence
gates remain in their current paths.

## Actual local verification

Environment: Node `v24.22.1`, V8 `13.6.233.17-node.58`, Linux x64.

```sh
node --test --test-reporter=tap .github/continuum/development-backlog.test.mjs .github/continuum/backlog-cli.test.mjs .github/continuum/backlog-contract-cli.test.mjs .github/continuum/backlog-investigator.test.mjs .github/continuum/backlog-investigator-recovery.test.mjs .github/continuum/backlog-repair-worker.test.mjs .github/continuum/owner-contract-worker.test.mjs .github/continuum/autonomous-teamleader.test.mjs .github/continuum/local-teamleader-loop.test.mjs
node tools/completed-connections-822e-r2/worker/selection-parity.mjs
git diff --check
```

- Node gate: exit 0, **112 tests passed**, 0 failed, cancelled, skipped or todo.
  Complete output: [node-tests.tap](node-tests.tap).
- Parity: exit 0, **40,960 exact object-identity assertions passed**, plus frozen
  input and canonical-byte nonmutation checks. These are assertions, not 40,960
  test methods. Sizes 0/1/20/100/500; mixed priority, ties, scoped and no-match
  modes; two deterministic seeds and all five optional filters. Source and
  harness SHA-256 values are recorded in [parity.json](parity.json).
- Whitespace/error check: exit 0.

Tests operate on temporary fixtures. No live worker or owner backlog was run.
No new timing or throughput measurement; no Android, APK, Fold7 or model test.
The parent owns combined integration and any wider C1 validation.

## Handoff and rollback

The parent can cherry-pick this local commit from branch
`integration/completed-worker-822e-r2` in
`/workspace/scratch/822e72396ed9/r2-worker`, review the six bounded files and
publish through #523. This child made no remote mutation. To remove this slice,
revert its commit; no data migration or caller change needs reversal.
