# Astra Jarvis/Continuum takeover — 2026-11-04

## Authorization and exact starting points

Owner request in this session: continue the supplied three-phase plan; reuse the existing brain sphere and avatar head in the new layout. No main merge, release, installation, force push, deletion, new permissions or cloud-provider activation. Preserve the original signing/update path and S01 gate. Draft PRs remain open. Never weaken tests or fabricate runtime events.

- Integration baseline: `578bdfd12f896cc990bf1a043bc1b248ba3adb6d` (PR #483). Reported upstream: 2371 total tests, 0 failures, 19 skipped, lint 0. **Not yet reproduced in this workspace.**
- Preserved untested navigation: `wip/stop-20261104-integration-nav` @ `69b823133e132c5535626edb34eb26327bebb86c`.
- Preserved untested status panel: `wip/stop-20261104-sicherheit-m5fg` @ `d1da1d1dbf31e25d4816d0df0362195e5e282a67`.
- Preserved untested design/glow: `wip/stop-20261104-welten-optik-design` @ `c7fc330d911af82dbb0baff4c2a56ed0e074b05d`.
- Memory candidate #371: `8948d2d6ee74916c68bbd1631f8f899831406612`; requires independent recovery safety review before integration.

The owner's latest M2.10 decision supersedes older undecided handoffs: automatic recovery remains; recovered owner facts stay inactive until explicit owner confirmation. A damaged revocation frame must never reactivate a revoked fact.

## Execution ledger

1. BASELINE — IN_PROGRESS: official JDK 17 and Android SDK 36 configured; unchanged baseline Kotlin/Java compilation completed. The native Android-View screenshot tests are running before the full unit/lint/build gate. The original workspace remains unchanged while parallel candidate work uses separate worktrees.
2. WIP RECOVERY — IN_PROGRESS: selected changes from all three saved branches are present in the candidate, not yet accepted. Imported source commits: `76469429`, `4d755c50`, `69b82313`, `34d3488b`, `c7fc330d`, `d1da1d1d`. The shared glow commit is imported once. Existing laser methods and Android 12 on-device speech restrictions are retained. Five new regressions await their RED run before additional fixes.
3. COGNITIVE LOOP / MEMORY — PENDING: reuse existing local model, orchestrator and ToolGate; implement bounded lifecycle/ReasoningModel/LoopMemoryPort contracts and actual-event bridges. Review memory recovery persistence and reconfirmation-cache behavior; do not duplicate the existing ThinkingMemory implementation.
4. MODEL PROVIDER — OWNER_GATED: no provider selected, no credentials requested or published, no external model calls. Existing local adapter work remains authorized.
5. ACTION SCENES — PENDING: actual tool transitions, explicit confirmation including exact outbound search query (F1), calm failures and cancellation; no fabricated successes.
6. AVATAR / ORB — PENDING: preserve the existing real-signal view; separate head/brain layout slots without losing state on resize; consolidate lip-sync/blink ownership.
7. FINAL HOST QA — PENDING: source-bound unit, UI, lint and build evidence; visual comparison with the existing references; independent review.
8. FOLD7 — NOT_RUN: no connected device identified. No device or GPU claims from host tests.

## Preflight findings (source inspection, not executed reproductions)

- Existing Activity signals target the original `ContinuumHologramView`, while the cockpit avatar adapter creates another view and hides the original. Test visible-state propagation and resize identity before fixing.
- Memory candidate writes reconfirmation sidecar after recovering the ledger and tolerates sidecar write errors. Test persistence across reopen/process restart before accepting it; also test temporal-cache invalidation after reconfirmation.
- Experiment compatibility: EXP-05 (existing local inference), EXP-06 (provenance), EXP-08 (existing transport), EXP-11 (single orchestrator/gate), EXP-12 (recovery/continuity). No second memory database, repair pipeline or worker framework.

## Recovery / continuation

Continue from this branch and its exact recorded tests. Existing bot branches remain unchanged. Preserve intermediate work as explicitly unverified checkpoints when necessary. Do not treat the old CLOSEOUT table or inherited test counts as current validation.

## Parallel ownership and current visual check

- Build/screenshot agent: untouched baseline and serial Gradle execution; exports the actual Activity's Android Canvas output. Host API 28 captures are not Fold7/Filament GPU evidence.
- Avatar agent: original live view, HEAD/ORB geometry, resize and lifecycle, genuine memory-event effects. No replacement GLB or duplicated director.
- Memory agent: explicit recovery adapter on the existing segment store; original strict readers, locking, bounded reads and tests remain. The incompatible flat-LEX3/LEX4 file is not substituted.
- Root: selected WIP integration, dense navigation, material/glow and live card wiring, independent integration review.

Owner steering: ref5 is the visual reference, including adjoining buttons and soft overlapping glow. Compare equivalent viewports and individual regions before accepting a change. Existing cached PNGs are historical and must not be labelled as new captures. No game-specific adapter is installed; the repository's existing Android capture harness is used.

The navigation WIP is five unique commits ahead of the integration baseline (including two merge commits), not 42 missing development changes. Safety's API-31 guard already exists in the baseline; only its new presentation changes are imported. Old author messages containing test claims refer to their original source commits, not this candidate.
