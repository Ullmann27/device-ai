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

1. BASELINE — IN_PROGRESS: configure local Android toolchain; reproduce compile/unit/lint on the exact baseline before application edits.
2. WIP RECOVERY — PENDING: compare all three saved branches against the baseline, preserve their work, integrate compatible changes on this branch with tests.
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
