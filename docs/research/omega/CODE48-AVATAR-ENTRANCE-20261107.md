# Code48 finite avatar entrance

Status: SOURCE_INTEGRATED / MOCK_TESTED. EXP-02/07.
Base: `6d52d74aba5bef0e9e0bfdc444245b3acd21b1d9`.
Production: `39bc504cd5cf3d003b3be453dcc5f190ea7b80a8`.
Cold-start test isolation: `669ed3211f5b7d045d3da7038ab015fe1100e37f`.

## Connection and bounds

`CockpitAnimationPresenter` -> `JarvisHoloAvatar.onCockpitCue` -> `ContinuumHologramView.playAvatarEntrance` now delivers AVATAR_FROM_DEPTH and AVATAR_SHRINK to the existing head surface.

`AvatarEntranceMotion` samples the existing hologram frame uptime. The head grows from scale 0.12 to 1.14 over 2 seconds, holds through the greeting and returns to normal from 3 seconds over 600 ms. SHRINK can request an earlier return; a late or missing cue cannot extend the 3.6-second sampled deadline. No new timer, Handler, animator or frame callback exists.

The transform is local to the Canvas head/aura and the native TextureView child composition. The whole hologram, original orb renderer and work-window camera are not scaled. Existing head-region clipping prevents entrance geometry from crossing into the orb region. STOP, background, reduced motion, visibility loss, focus loss and detach reset immediately. Initial window focus is not required to accept a visible foreground boot; otherwise the first pre-draw could consume the cue before focus arrives.

All original assets, `ContinuumHologramRenderer` and `AvatarCameraMotion` are unchanged from the base. Tool/consent gates, local inference, memory, SWB1 and P12 are unchanged.

## Actual startup integration

The initial Activity boot is emitted before the avatar slot is ready. Root's Presenter change waits for an active, laid-out, attached avatar stage through pre-draw. The test compiles that external working-tree Presenter with an explicit SHA256 in the receipt; this branch does not contain a copied Presenter.

The cold-start test uses the existing `resetBootForTest()` because `bootOnce()` intentionally has a process-wide flag and another Activity test may already have consumed it. The production boot is never manually re-emitted in the first-launch test. Pre-draw is dispatched as part of the host Canvas traversal.

## Five focused real-view tests

| Test | Result |
|---|---|
| `realPresenterMovesOnlyTheHeadAndFinishesTheBoot` | PASS: head-region pixels change; original orb region, renderer, geometry, phase, energy, audio and state weights remain identical at the same clock instant; entrance settles |
| `shrinkCueReturnsTheHeadWithoutChangingTheWorkCamera` | PASS: early SHRINK returns the head while the accepted work camera remains active |
| `lifecycleResetsImmediatelyWithoutReplayingTheEntrance` | PASS: background, reduced motion, hidden view, focus loss and detach |
| `actualOwnerStopImmediatelyCancelsTheEntranceAndItsDelayedCue` | PASS: actual `AgentActivity.cancelLocalRequest` |
| `actualActivityStartupDeliversEntranceAfterFirstLayout` | PASS: real Activity first startup, no manually supplied BootStarted |

Production compilation, test compilation and JUnit exit 0; total 5 tests, 0 failures. Commands, source hashes and raw logs are under `docs/evidence/code48-avatar-entrance-20261107/`.

Four host fallback captures (rest, depth, foreground, settled) were visually inspected and have recorded hashes. The existing aura extends beneath the transparent orb background; moving it changes composited background pixels there. No whole-orb pixel-equality claim or percentage-based visual gate is used.

This is an SDK/Robolectric/native-Canvas host run using a freshly compiled overlay. It proves actual Presenter/View wiring and fallback pixels. It does not prove native Filament GPU pixels, Fold7/Android17 behavior, a new APK, temperature/FPS savings or acceptance against the reference video.

## Integration and rollback

Keep both `cockpitSequence?.clear()` from Root and `resetAvatarEntrance()` from this commit in `clearTaskEffects`. Integrate Root's tested Presenter readiness patch before building the combined candidate. Retain the original assets and orb. Rollback the entrance code commit; owner data is unaffected.
