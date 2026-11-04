# Branch bots — serial continuation 2026-11-04

Status: WIP / UNVERIFIED. Starting source: PR #515 at 5373ec7c1399cb34fb8896f7318fdfa5437fa283. All 104 locally materialized code/test files match the exact remote blobs. This is a selected source snapshot, not a full Android checkout.

## Coordination

C1 #519 owns shared application entry points. PR #527 at d2aedf2c272bfa20d4e1930a0f65277c3f02be0f reports intake of nine exact #515 source/test files; those changes must be preserved. This continuation changes only its own branch-discovery/CLI/host-diagnostic slice and its tests. It does not integrate or edit another team's active branch.

## Sequential milestones

1. Reproduce bounded-discovery starvation and add source-bound page continuation. Every report must retain honest per-page coverage; no copied cursor becomes execution authority.
2. Connect continuation to the existing finite scheduler and optional single-pass resume, with private atomic state, safe restart and unchanged proposed-only backlog.
3. Exercise the APK-toolchain diagnostic against near-match versions and invalid file shapes. Repair only reproduced false admissions; do not install tools or claim an APK.
4. Verify the combined selected suite, published source identities and compatibility with the current integration intake. Save exact evidence and a ready-for-next-iteration handoff.

Relevant experiment contracts: EXP-00 (diagnosis only), EXP-01 (bounded local cadence), EXP-11 (canonical backlog), EXP-12 (source-bound recovery). Baseline 183 PASS remains historical until a new actual combined run. No parallel agents in this round.

## Authority and limits

No main merge, package/signing/model change, paid model API, Actions invocation, release or installation. No new daemon or competing task framework. The existing owner/CCC1 workflow still decides execution. Android compilation, device test and APK acceptance remain NOT_RUN.

## Checkpoint protocol

The accompanying state.json is ACTIVE during this session. A scheduled continuation must not edit an ACTIVE checkpoint or infer that elapsed time alone makes another session inactive. Once this round is complete, state becomes READY_FOR_NEXT_ITERATION with exact evidence and next scope. Missing access is a blocker, never an invented continuation.

Rollback: individually withdraw this round's bounded source changes; retain all earlier commits and evidence.

## M1 — bounded discovery progression

Reproduced the original starvation with five real Git fixture branches: three bounded passes repeatedly selected the same two sources. Three new regressions failed before the change; the full scanner/CLI subset now passes 27/27, zero failures/skips.

The scanner returns a source-root/checkout/target/ref-snapshot-bound nextCursor. Resumed pages report their own range and remaining sources. Even the final partial page never claims a full inventory. Malformed, out-of-range and stale cursors are rejected. Serialized navigation is not proposal authority; existing in-process report admission remains unchanged. CLI persistence is the next milestone, not yet implemented at this checkpoint.

## M2 — durable continuation

Five initial continuation regressions failed before the connection. After implementation and three additional boundary checks, the scanner/CLI/scheduler/preflight subset passes 61/61, no failures/skips. This includes real child-process restarts and SIGTERM during diagnosis, retained live-lock ownership, malformed/changed state and report bytes, source-ref changes, path truncation and deferred proposals.

Finite loops now save a private, hash-checked navigation cursor under the existing scheduler's lock implementation. The lock function is exported without changing its body or the scheduler's state/outcome vocabulary. Single invocations opt in with --once --continue-scan. Plain --once keeps its previous incomplete-inventory failure behavior.

A successful page has pageComplete=true but complete=false unless the report itself covers the whole source inventory. Path truncation stays ERROR and advances to subsequent sources; proposals skipped only by the per-pass budget are revisited on later sweeps. A full canonical backlog remains a reported capacity limit. Every task remains PROPOSED; there is no automatic execution.

The cursor is navigation only, not accumulated full-inventory or APK evidence. Ref/target drift restarts at the first page and records scanRestarted. Changed configuration, corruption and unsafe files fail closed. After a crash before cursor commit, canonical proposal identities deduplicate replay. Terminal scheduler budgets remain terminal; an explicit new single-pass command is a separate invocation, not a reset of an old budget.

Example from a complete approved checkout:

~~~sh
node .github/continuum/branch-bots-cli.mjs --root "$PWD" --state "$HOME/.local/state/continuum-branch-scan" --once --continue-scan --max-sources 40
node .github/continuum/branch-bots-cli.mjs --root "$PWD" --state "$HOME/.local/state/continuum-branch-scan" --loop --max-cycles 12 --interval-minutes 60 --max-sources 40
~~~

Use the same scan settings for the same private state directory. An exhausted loop session needs an explicitly separate session or a one-shot continuation; it does not silently acquire a new cycle budget. This session has not started such a device process.

## M3 — precise host tool profile

Three new tests reproduced false host admission: Gradle 8.11.10 matched an 8.11.1 substring, a directory satisfied the SDK platform check, and a non-executable AAPT2 file passed. The corrected diagnostic requires one exact stable Gradle 8.11.1 line, a nonempty readable regular platform file, and a nonempty executable regular AAPT2 file. Near-match prereleases, contradictory/duplicate Gradle banners, empty files and symlink loops are covered.

The relevant CLI/host/mobile-builder subset passes 72/72, no failures/skips. Existing positive fixture AAPT2 entries now have executable permissions. These shell fixtures are not real Android tooling. Executability does not prove AAPT2 ABI compatibility, SDK contents, dependency cache or successful compilation; hostReadyToAttemptQa remains only a preflight indication. The actual APK/device gates remain NOT_RUN.

## M4 — genuinely read-only source audit

Three actual Git fixture regressions were red before the fix. The old release source audit executed a configured fsmonitor helper and a configured clean filter (both harmless marker scripts), and accepted a current-worktree gitlink. The release audit now reuses assertBranchSourceSafe before obtaining cleanliness; unsafe configuration/submodules are rejected before status. A genuinely dirty checkout still returns the existing UNCLEAN_CHECKOUT blocker instead of changing the source-admission contract.

The combined scanner/release-preflight subset passes 42/42, no failures/skips. This is a connection to the existing reader policy, not a second Git sandbox. The module now intentionally imports the existing branch-discovery guard from the repository. No APK was built and no external helper beyond the controlled fixtures was run.

## M5 — bounded report volume

A real Git fixture with 81 distinct source commits reproduced SERVICE_RECORD_TOO_LARGE under the previous default scan: the private 4 MiB record writer rejected the completed report, so no continuation could advance. Discovery now budgets its source report at 3 MiB, leaving room for the CLI's small environment diagnostic within the existing 4 MiB storage contract. A byte boundary creates another source page rather than an oversized write.

The scanner/CLI subset passes 37/37, no failures/skips. It visits all 81 fixture source commits across actual process restarts. A separate 2,000-long-path source proves that a single oversized branch keeps its exact change totals while truncating detail explicitly; coverage remains incomplete and no APK or merge authority is granted. Oversized metadata or an individually unrepresentable record still fail closed. Git-output, time, proposal and canonical-backlog limits remain intact.

## Current integration intake comparison

Pinned PR #527 at ca80459a3f6695c8fecff4a217fbe93318452121 was compared against all 104 selected baseline code/test blobs. Exactly two differ: development-backlog.mjs contains the existing one-pass task-selection optimization, and the owner QA test arranges the same signing exclusions in its common negative-gate table. The seven files changed by this continuation have matching old preimages. Preserve both upstream files; never replace the complete current integration tree with this older branch base. A selected compatibility test and a bounded patch are being prepared.
