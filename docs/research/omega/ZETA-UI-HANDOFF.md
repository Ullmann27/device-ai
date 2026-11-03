# ZETA — responsive native cockpit

Source checkpoint: `de86cff67b1b028645d7bf77f5fee171d66530a0`. Baseline Chief: `04b4292d796bc151c40ab18405226cd9faef01cf`. Branch: `codex/zeta-ui-20261103`. Draft PR: https://github.com/Ullmann27/device-ai/pull/408. Quota: `NOT_VISIBLE`; work is checkpointed regularly.

Status: SOURCE_IMPLEMENTED and HOST_TESTED. Frozen source passes nine layout tests, two native UI tests (15 actual widget snapshots and font1–2 text bounds with long values), and three Java2D screen/data regression tests. Independent Challenger owns a separate, pre-frozen holdout; its final verdict remains pending. No full Android/physical Fold7/release claim. No main merge, release, signing, install, permissions or owner-data changes.

## Contract frozen before edits

The former layout scales all coordinates and text with window width. At320dp header controls shrink below48dp and portrait Fold uses three card columns even at600dp. The new owner reference requires an unobscured central avatar/orb, readable glass cards, clear navigation and adaptive phone/Fold/tablet composition.

Hypothesis: fixed-dp controls/text with reflowed1/2/3-column cards and actions below the hero keep all existing functions reachable without shrinking the desktop. Same `ContinuumDashboardState` and `openHomeSlot` callbacks; no fabricated model, memory or worker statistics. Unknown values remain unknown. Original speech input stays Activity-owned and pinned.

Acceptance frozen at `07c855b1`, before implementation:320/412dp phone,600/840dp Fold,1280dp tablet; no intersecting clickable slots; header/action targets at least48dp; free hero; all real cards scroll-reachable; same callback-slot mapping; no example values in empty state; font1.3+ checks. Baseline geometry/render evidence remains in `docs/evidence/zeta-ui-20261103/baseline`.

The previous exact scaled-position and three-column portrait assertions are explicitly superseded by the owner's new adaptive design. Original test sources and six-pass result remain archived. Fold-width reflow and IME/no-restart classification checks remain in the active suite. No safety or owner-data assertions are relaxed.

## Implemented connections

`ContinuumHomeLayout` now emits dp coordinates with scale1. Header buttons remain48dp. Free hero above action tiles; phone one card column, Fold two, broad tablet three with status cards beside the stage. Larger type reflows columns and increases row height instead of shrinking everything. Font support now reaches2.0 within Home.

`ContinuumHomeScreen` uses new native `CockpitActionTile`/`CockpitMetricCard` elements driven by the existing state. Actions: Chat, Forschung, Lernen, Entwicklung, Verbindung, Lernstatus. Cards: Gehirn & Modell, Lernen, Gedächtnis, Entwicklung, Aufträge, System & Gerät, Diagnose. All slots keep existing Activity callbacks. Existing speech input, owner/developer navigation, ToolGate and account permissions are unchanged.

Home keeps the same hologram instance. Wide free stages use existing FOLD geometry to avoid the legacy portrait halo clipping; phone uses existing PHONE geometry. No renderer implementation, state machine, avatar sink or model inference is added. EPSILON owns actual3D and shares exact hero dimensions (see evidence README).

## Scope and verification

Owned production: HomeLayout, HomeScreen, HomeElements, HomeView and new CockpitElements. No Activity, HologramView, build configuration, avatar mesh, TTS, camera, model, tool or persistence edit.

Robolectric images are actual native Home widget Canvas, not full APK or Fold7 evidence. Preserve all source SHA, commands and raw logs under `docs/evidence/zeta-ui-20261103`. Full Gradle/Android verification belongs to Quality and runs after brain integration gates. Independent challenger `/root/zeta_challenger` freezes its own unknown cases before inspecting this implementation.

## Remaining work and resume

1. Read final exact branch HEAD and final raw evidence; do not infer success from this checkpoint.
2. Resolve any independent holdout failure before integrating.
3. Run final Quality build/tests on the actual combined Chief source.
4. Combine EPSILON actual3D within the agreed free stage; preserve current orb and fallback.
5. Capture actual full Activity screenshots, including existing navigation/composer and keyboard at320/412/600/840/1280dp.
6. Verify TalkBack, display zoom, fold/unfold/rotation and foreground lifecycle on Android; no physical device is attached here.
7. Evaluate readable Activity-owned navigation and responsive pinned input in a separately leased increment if needed.
8. Complete user/developer screen organization without treating hidden navigation as authorization.
9. Compare approved reference with actual3D integrated screenshots, explicitly separating visual identity from widget layout.
10. Measure real Fold7 frame time, RAM, power and Gemma coexistence before any release proposal.

Rollback: revert this isolated UI composition and elements; no data migration or memory/model state change is involved.
