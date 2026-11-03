# ZETA — responsive native cockpit

Status: WIP / UNVERIFIED. Baseline Chief `04b4292d796bc151c40ab18405226cd9faef01cf`. Branch `codex/zeta-ui-20261103`. Quota `NOT_VISIBLE`; checkpoint every meaningful increment. No main merge, release, signing, install, permissions or owner-data changes.

## Contract frozen before edits

The current layout scales all coordinates and text with window width. At320dp header controls shrink below48dp and the portrait Fold uses three card columns even at600dp. The new owner reference instead calls for an unobscured central avatar/orb, readable glass cards, clear navigation and real adaptive phone/Fold/tablet composition.

Hypothesis: fixed-dp controls/text with reflowed1/2/3-column cards and actions below the hero will keep all existing functions reachable without shrinking the whole desktop. Same `ContinuumDashboardState` and `openHomeSlot` callbacks remain; no fabricated model, memory or worker statistics. Unknown values stay unknown. The original speech input remains Activity-owned and pinned; this lane does not replace it.

Owned files: `ui/ContinuumHomeLayout.kt`, `ContinuumHomeScreen.kt`, `ContinuumHomeElements.kt`, optionally `ContinuumHomeView.kt`; one new `ContinuumCockpitElements.kt`; focused geometry/widget tests and this lane's evidence. No `ContinuumHologramView`, Gradle, AvatarDirector, Activity, model, tool or persistence edits. EPSILON owns actual3D and keeps original orb inside the hero.

Acceptance:320/412dp phone,600/840dp Fold,1280dp tablet; no intersecting clickable slots; header/action targets at least48dp; hero unobscured; all real cards present/scroll reachable; same callback-slot mapping; no sample values in empty state; font-scale1.3 and larger smoke checks. Freeze baseline render/geometry before implementation, retain failures. Actual Robolectric images are host Android Canvas screenshots, not Fold7 evidence. Java2D previews are labelled separately. Full Gradle/Android gate is owned by Quality and runs after brain gates.

Existing tests assert old exact scaled positions and three-column portrait geometry. Those expectations are intentionally superseded by the latest owner design; preserve baseline report and replace with stronger new accessibility/no-overlap/content invariants. Fold-width reflow and IME/no-restart assertions remain.

## Entry and rollback

Read exact branch HEAD and evidence first. Existing renderer and UI event contracts remain. Revert this isolated layout/rendering change to restore previous composition; no data migration. Current visual/render/test results NOT_RUN at this initial checkpoint.
