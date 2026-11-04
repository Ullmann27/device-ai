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
