# Avatar/cockpit connection handoff — 2026-11-04

## Scope and provenance

- Local branch: `bot/avatar-cockpit-live-wiring`.
- Base: `e5c06e6bf657db556bc4861c7f46a55ebcd6e03a` (#506 integration checkpoint).
- Source: #505, `4ddbcb092d955226bb83c2a10f7ac52bb34f59eb`, using the coordinator-verified read-only checkout `/workspace/scratch/7dcf5d78f2cb/continuum-repair`.
- Imported exactly, without further changes: `app/src/main/java/at/heinz/deviceai/ui/JarvisSequenceView.kt` and `app/src/uiTest/java/at/heinz/deviceai/ui/JarvisSequenceTurnLifecycleUiTest.kt`.
- This is the existing #505 presentation lifecycle repair, not a new runtime design. MainActivity, avatar assets, audio production code, core, package/version/signing configuration, and release workflows are unchanged.

## Connected behavior

Boot, navigation, calculation, and global status events no longer acquire the sequence view's work-card slot. A worker's `ActionStarted` can therefore display its card without a preceding `CommandFlyIn`.

The view remembers the 24 most recently completed or displaced turn IDs. Delayed commands and work callbacks for those turns cannot reopen completed cards or displace the current turn. Completion is remembered even when its turn was never visible. Existing memory signals and global extension effects remain independent. Motion pause retains completed-turn history without declaring an active turn completed.

## Verification and limits

- Both imported files were byte-compared against the read-only #505 source.
- Added the unchanged 11-case Robolectric suite `JarvisSequenceTurnLifecycleUiTest`. It drives the real `CockpitAnimationBus` into the real `JarvisSequenceView.render` path and asserts the resulting card/recall state. Renderer exceptions are collected and fail the test teardown rather than disappearing inside the bus.
- `git diff --check`: PASS on this scoped contribution before commit.
- No Gradle process was started in this checkout: the coordinator owns the combined source compile and Android/Robolectric run in its isolated build environment. This handoff does not claim these 11 tests have passed in the combined tree.
- The synthetic event tests do not establish on-device worker execution, Android rendering acceptance, or physical-device lifecycle acceptance. The separate #495 live TTS callback test is integrated by the coordinator.
- The avatar `rig_hierarchy_orientation` gate remains open. No GLB or other unaccepted asset bundle was imported; no asset acceptance is claimed.

## Integration and rollback

Cherry-pick this contribution onto the coordinator's #512 branch, then run `JarvisSequenceTurnLifecycleUiTest` alongside the live TTS and existing avatar/cockpit integration tests during the combined Android validation. No APK build, install, signing, release, paid service, or GitHub Actions run is part of this contribution.

Rollback is a single revert of this contribution's commit, which restores the previous view and removes the added test and handoff. No migration or persistent data change is involved.
