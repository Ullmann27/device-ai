# Astra Jarvis/Continuum takeover — 2026-11-04

## Authorization and exact starting points

Owner request in this session: continue the supplied three-phase plan; reuse the existing brain sphere and avatar head in the new layout. No main merge, release, installation, force push, deletion, new permissions or cloud-provider activation. Preserve the original signing/update path and S01 gate. Draft PRs remain open. Never weaken tests or fabricate runtime events.

- Integration baseline: `578bdfd12f896cc990bf1a043bc1b248ba3adb6d` (PR #483). Independently reproduced: 2371 total tests, 0 failures, 19 skipped (2352 passed); configured NewApi lint gate 0; assembleDebug passed. Only a local QA APK was built, not installed or released.
- Preserved untested navigation: `wip/stop-20261104-integration-nav` @ `69b823133e132c5535626edb34eb26327bebb86c`.
- Preserved untested status panel: `wip/stop-20261104-sicherheit-m5fg` @ `d1da1d1dbf31e25d4816d0df0362195e5e282a67`.
- Preserved untested design/glow: `wip/stop-20261104-welten-optik-design` @ `c7fc330d911af82dbb0baff4c2a56ed0e074b05d`.
- Memory candidate #371: `8948d2d6ee74916c68bbd1631f8f899831406612`; requires independent recovery safety review before integration.

The owner's latest M2.10 decision supersedes older undecided handoffs: automatic recovery remains; recovered owner facts stay inactive until explicit owner confirmation. A damaged revocation frame must never reactivate a revoked fact.

## Current integrated result

The selected WIPs, visual refinement, original avatar/orb wiring and reviewed memory recovery are integrated. The completed standard unit gate is bound to local source `f14ce8e9d6a3864117a9cea3d325024ab4ff8d9b`; subsequent additions are UI tests and documentation only. Its `app/src/main` Git tree is `9228dfb380983f59c96ac8d1fcd290b99624cd44`. The remote snapshot is delivered through open **Draft PR #491**, based on PR #483's integration branch, with exact local/remote tree equality verified when publishing.

### Interface and genuine events

The single Activity-owned menu rail now reserves space once. Buttons visually adjoin while keeping separate >=48 dp touch rows. Shared cyan/violet materials, soft layered glow, actual vector icons and truthful owner/status content are used by the cockpit. The original live hologram view retains its state/audio/director ownership; separate HEAD and ORB regions fix the previous collision and squeezed sphere. Docking/resizing no longer rewrites the saved 3D preference. Curved fibres and revised portrait framing improve the Canvas fallback without replacing the original GLB.

`MemoryEventSources.forApp(filesDir)` now subscribes to the real app-scoped hub through the existing source adapter. Research start does not manufacture document arrivals; only genuine confirmed writes produce a confirmed arrival effect, hypotheses remain distinct, and pause/detach reject late visual callbacks. Unknown counters and absent sensors remain unknown. The hourly Memory Activity chart still lacks a live producer and therefore remains empty rather than showing invented bars.

The current picture and exact render provenance are under `docs/evidence/astra-takeover-20261104`. Visual source `41433ff0` is unchanged by the later recovery/caller work. The picture is a production Java2D component preview of the Canvas fallback, **not** an Activity/Fold7/Filament capture or a claim of exact ref5 likeness.

### Recovery and caller safeguards

The existing compatible LEX2 / segmented LEX3 v1 store now supports the explicitly selected automatic recovery path, without substituting #371's incompatible flat format. Verified surviving data is recovered, damaged evidence preserved, and a durable consent marker written before a recovered primary is published. Restored owner facts remain inactive until the trusted owner's new decision binds the exact displayed IDs/content hashes and review fingerprint.

The production Activity uses the reviewed recovery factory on its existing I/O executor. The memory center provides the explicit review dialog, initially selecting no facts, with lifecycle cancellation and stale/write-failure handling. Existing strict archival readers remain strict. A recovery history, including a completed empty marker, blocks re-import of old preference facts under the same store transaction; a fresh explicit owner confirmation remains available.

Legacy `BootstrapBrain` overview, structured-knowledge and revision routes also read currently eligible Core facts through the existing executor. The memory display no longer treats raw old preferences as current consent. Callback token ownership, session identity and cancellation are checked before presentation; STOP and a later typed draft are preserved. The ordinary dialogue callback also rejects cancelled/stale-session output. Independent source review approved this combined route; actual Caller tests now pass.

### Verification and remaining scope

- Complete standard Gradle unit suite: **2416 total / 0 failures/errors / 19 skipped** (2397 passed), 434 suites, successful complete compilation. Baseline was independently reproduced at 2371/0/19.
- **21 targeted real Robolectric UI cases passed** on the same fully compiled application. Seven new Caller cases use `09e23507`; the other fourteen cover original avatar view/viewport, genuine events, attach/detach and explicit owner review. No product assertion was removed or weakened.
- Configured NewApi-only lint gate: 0 issues, exit 0. `assembleDebug`: successful local QA APK, exit 0; no installation or publication. Exact source binding, hashes and any additional host gate are in `docs/evidence/astra-takeover-20261104/final-qa.json`.
- Full Activity capture/lifecycle gate is still open after a bounded pre-assertion Robolectric traversal timeout; no Fold7 is connected. Java2D/LEGACY tests do not prove native GPU appearance or performance.
- Original GLB provenance remains unresolved; its bytes are unchanged. Ref5 facial anatomy, 3D surface shading, final native glow and actual-device typography/animation still require native comparison. This is not a pixel-perfect or release acceptance.
- Broader CognitiveLoop/ReasoningModel/LoopMemoryPort and action-scene work, unconnected calculation/chart producers and owner-gated provider/permission decisions remain follow-up scope. Existing local model/orchestrator/ToolGate/ThinkingMemory are reused, not replaced. F1 exact outbound query approval remains mandatory before attaching the document-derived web search.
- After a real recovery has occurred on a device, do not roll back to an older reader that ignores the consent marker without first re-blocking restored facts. No device installation occurred here.

## Initial execution ledger (historical checkpoints)

1. BASELINE — PASS: official JDK 17 and Android SDK 36; unchanged application source compiled, unit suite, configured NewApi lint and assembleDebug passed. Separate serial gates succeeded after an earlier concurrent Gradle attempt lost its daemon. Native screenshot gate remains NOT_RUN to completion (see evidence README).
2. WIP RECOVERY — IN_PROGRESS: selected changes from all three saved branches are present in the candidate, not yet accepted. Imported source commits: `76469429`, `4d755c50`, `69b82313`, `34d3488b`, `c7fc330d`, `d1da1d1d`. The shared glow commit is imported once. Existing laser methods and Android 12 on-device speech restrictions are retained. Five new regressions failed on this combined WIP, then all five and 26 existing related tests passed on local fix `e33a4dd9`. The latest combined candidate has additional changes and still needs its own final gate.
3. COGNITIVE LOOP / MEMORY — PENDING: reuse existing local model, orchestrator and ToolGate; implement bounded lifecycle/ReasoningModel/LoopMemoryPort contracts and actual-event bridges. Review memory recovery persistence and reconfirmation-cache behavior; do not duplicate the existing ThinkingMemory implementation.
4. MODEL PROVIDER — OWNER_GATED: no provider selected, no credentials requested or published, no external model calls. Existing local adapter work remains authorized.
5. ACTION SCENES — PENDING: actual tool transitions, explicit confirmation including exact outbound search query (F1), calm failures and cancellation; no fabricated successes.
6. AVATAR / ORB — IN_PROGRESS: preserve the existing real-signal view; separate head/brain layout slots without losing state on resize; consolidate lip-sync/blink ownership.
7. FINAL HOST QA — PENDING: source-bound unit, UI, lint and build evidence; visual comparison with the existing references; independent review.
8. FOLD7 — NOT_RUN: no connected device identified. No device or GPU claims from host tests.

## Initial preflight findings (historical source inspection)

- Existing Activity signals target the original `ContinuumHologramView`, while the cockpit avatar adapter creates another view and hides the original. Test visible-state propagation and resize identity before fixing.
- Memory candidate writes reconfirmation sidecar after recovering the ledger and tolerates sidecar write errors. Test persistence across reopen/process restart before accepting it; also test temporal-cache invalidation after reconfirmation.
- Experiment compatibility: EXP-05 (existing local inference), EXP-06 (provenance), EXP-08 (existing transport), EXP-11 (single orchestrator/gate), EXP-12 (recovery/continuity). No second memory database, repair pipeline or worker framework.

## Recovery / continuation

Continue from this branch and its exact recorded tests. Existing bot branches remain unchanged. Preserve intermediate work as explicitly unverified checkpoints when necessary. Do not treat the old CLOSEOUT table or inherited test counts as current validation.

## Parallel ownership and current visual check

- Build/screenshot agent: untouched baseline and serial Gradle execution; attempted the actual Activity capture, which blocked before Activity construction. The delivered component preview uses production painters with Java2D; it is not Fold7/Filament GPU evidence.
- Avatar agent: original live view, HEAD/ORB geometry, resize and lifecycle, genuine memory-event effects. No replacement GLB or duplicated director.
- Memory agent: explicit recovery adapter on the existing segment store; original strict readers, locking, bounded reads and tests remain. The incompatible flat-LEX3/LEX4 file is not substituted.
- Root: selected WIP integration, dense navigation, material/glow and live card wiring, independent integration review.

Owner steering: ref5 is the visual reference, including adjoining buttons and soft overlapping glow. Compare equivalent viewports and individual regions before accepting a change. Existing cached PNGs are historical and must not be labelled as new captures. No game-specific adapter is installed; the repository's existing Android capture harness is used.

The navigation WIP is five unique commits ahead of the integration baseline (including two merge commits), not 42 missing development changes. Safety's API-31 guard already exists in the baseline; only its new presentation changes are imported. Old author messages containing test claims refer to their original source commits, not this candidate.

## Earlier visual integration checkpoint (historical e19 state)

Local candidate `e19df153` combines the shared window/rail geometry, reference tile materials/icons, truthful owner-card content and avatar wiring. Local follow-up `f43b521e` also gives native AGSL the same measured colour tokens as the portable painter; native shader execution remains unverified here.

- A single Activity-owned rail; adjoining visual buttons retain disjoint >=48 dp touch rows.
- Same original avatar view and state/audio ownership; distinct portrait and original orb regions, no forced saved appearance change.
- Original GLB asset unchanged; provenance discrepancy still requires resolution before replacing/distributing an asset.
- Genuine memory-event component tests: 5/5 passed on avatar fix `17ec08e9`, after 3/4 expected baseline failures. No invented document arrivals from a research-start event.
- Root panel/navigation fix: 31/31 targeted tests passed on `e33a4dd9`, after five expected combined-WIP failures. New rail and owner-card assertions await their own recorded run.
- Recovery implementation is developed separately, not yet activated in the production Activity at this checkpoint. `MemoryEventSources.production` remains gated until safety review.
- The displayed baseline image is under `docs/evidence/astra-takeover-20261104`; candidate capture is pending. Never label a Java2D component preview as a live Activity/device screenshot.

Next: finish the candidate preview and functional wiring tests, review recovery and its explicit owner-reconfirmation path, run combined unit/lint/build gates, and publish exact evidence. Provider selection, connected-device testing and unconnected ActionScene/Calculation sources remain explicit follow-up work.
