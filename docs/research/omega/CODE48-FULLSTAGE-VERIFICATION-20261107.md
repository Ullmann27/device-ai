# Code48 fullstage memory episode

Base: `d73fc46b5a14ea5fc3b82a3bd3b96cacebe7d4de`.
Feature checkpoint: `284716538321cdf2d1f109aa57cf33eec907a64c`.

## Actual connection

Positive existing `MEMORY_TURN` document/recall events pass through the real cockpit Presenter and avatar adapter into `JarvisSequenceView`. Its existing memory branch requests the Home-hosted scene through the live HologramView. Empty recall, unrelated scopes and ordinary memory writes do not start this episode. The local file-tunnel fallback retains its existing 3,000 ms contract.

The new episode has one fixed 4,800 ms deadline. Document flight leads into the enlarged original orb and then back to its current cockpit bounds. Repeated events cannot extend the episode. Paper/line and folder fills are decorative geometry driven by a real event; they are not invented document counts or progress measurements.

`ContinuumHomeView` owns the non-interactive fullstage child. It explicitly excludes this child from avatar-slot sizing, draws within the visible scroll viewport and clips out actual visible control-view rectangles and navigation. Native work windows remain above it in the Activity content container. The head, appearance preferences and normal cockpit layout are not replaced.

The existing `ContinuumHologramRenderer` instance and live frame draw the orb. Its normal small drawing is omitted only during the episode. The existing HologramView frame callback invalidates the new layer; no new timer, renderer instance, service, asset or rendering thread is introduced. STOP, background, hidden host, motion-policy rejection, detach and avatar reparent clear the temporary scene. Canvas levels and temporarily boosted actual-event energy are restored in `finally` blocks.

## Executed evidence

- Native-host RED against d73: the real Presenter first delivers the recall (`recallCount` checked), but no changed pixels reach outside the avatar slot. The assertion then fails as expected.
- The identical host assertion is GREEN with the new production overlay.
- Nine focused checks pass: one actual host drawing check, three pure-controller deadline/cancellation/order checks and five existing Recall/STOP/no-replay checks adapted to the new Home drawing surface. Direct `Video3FileTunnel` tests and their 3,000 ms boundary remain unchanged.
- Independent reviewer: five additional checks pass on checkpoint 284716538 with actual AgentActivity/cockpitBus, enlarged original orb and renderer geometry restoration, event flood/false signals, STOP/reparent/reduced motion, real window Close/system navigation and complete control clipping at hdpi. The coordinator receives that review's separate source-bound receipt and commit.
- One final targeted production compile and Native-host check pass after adding `finally` to the outer Home canvas scope. This is the only production change after the independent five-test run; it does not alter normal scene rendering.

Raw commands, logs, matching RED/GREEN test hashes and final production source hashes are under `docs/evidence/code48-fullstage-20261107/`. `productionCommit` in the focused runner receipts identifies the d73 compiled dependency cache; the overlay source hashes identify the changed production source. Early diagnostic harness runs are not counted as acceptance evidence.

## Limits and next gate

This is Android Native Canvas on Robolectric SDK28, compiled against SDK36. It is not physical Fold7 playback, Filament GPU validation, temperature measurement or a claim of exact video reproduction. All-source Android/Node/backend gates and the unsigned APK must run on the coordinator's final combined commit. Original signing and publication remain separate owner-approved release steps.

Rollback: revert the fullstage feature and follow-up commits together; no persisted-data migration is involved. The prior bounded avatar-slot tunnel remains available.
