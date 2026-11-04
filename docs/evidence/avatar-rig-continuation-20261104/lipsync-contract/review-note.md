# LipSyncFaceSource integration failure: contract-test correction

Date: 2026-11-04. Follow-up to the six-file avatar-motion integration.

## Result and source binding

The failure is caused by two obsolete assumptions in `sharedOpeningMatchesAvatarMorphMapper`, not by a missing LipSyncFaceSource connection to the visible avatar. No production code was changed.

- Root's failing integrated source: `dccd0901b2f300fde268d35e533d18b79664f20e` (publication reported by Root as `9ef9bd1`).
- Root reported 167 tests with one failure. The class XML copied here independently records `PP expected:<1.0> but was:<0.0>` at the previous line 73.
- Child patch commit: `04e92598f2bee9eb327a77d057b808d3f5ac687d`, parent `0be6f579c0dd60ffa1266c3ab7866360d1a0e8e2`.
- Exactly one repository file changed: `app/src/test/java/at/heinz/deviceai/motion/LipSyncFaceSourceTest.kt`, 49 insertions and two deletions.
- `git diff --check` and the staged equivalent passed. Worktree clean after commit. No direct push, production change, Root-file edit, Gradle run or JUnit run by the child.

## Static production-caller finding

The search is bound to the actual failing integration SHA, using `git grep` over `app/src/main/java`; its complete relevant output is retained in `production-callers.txt`. Source hashes are in `manifest.json`.

1. `ContinuumHologramView` obtains the current `AvatarPoseReducer` pose and calls `native3d.render(pose, avatarSeconds, ...)` directly.
2. `FilamentAvatarRenderer.render` invokes `AvatarMorphMapper.update` and supplies that mapper's weights to Filament. The corresponding call lines are preserved in `visible-render-path.txt`.
3. `MotionLibrary.faceWeights` registers a `LipSyncFaceSource` by default. `AgentActivity.subscribeRealSignals` installs the real `voiceSignal` level in `MotionLibrary.audioLevel`, and resets it when the subscription closes.
4. No production consumer of `MotionLibrary.faceWeights` or call to the `LipSyncFaceSource.applyTo` bridge was found. The bridge declaration and its existing unit test are present. `MotionLibrary.visemeHint` has no production assignment beyond its default null declaration.

Consequently, changing `FaceMotion.kt` would alter an unused alternate interface without repairing the actual visible-renderer path. The current change deliberately stays in the regression contract. The new test calls the real source and bridge classes, but does not claim that this sequence is a live app connection.

## Exact cause of the failing assertion

The authored `SHARED_OPENING` coefficients did not change. PP still has zero authored mouth opening. The renderer now additionally applies an explicit active-PP lip seal: it clears outgoing opening morphs and suppresses measured jaw opening for that pose. The former unconditional formula `1 - SHARED_OPENING[PP] == 1` therefore no longer describes PP's complete rendered result; expected rendered JawOpen is zero.

The old test also reused one mapper while changing every viseme at the same `1f` timestamp. Repeated timestamps are now idempotent for an active blend. After the PP case, subsequent visemes would therefore depend on that earlier state rather than independently test their coefficient mapping. This second issue is distinct from the first observed PP failure.

## Preserved and strengthened assertions

`sharedOpeningMatchesAvatarMorphMapper` remains present and covers all 15 visemes. It now creates a fresh mapper for each static sample and compares the complete 21-element weight array, not only JawOpen. Expected viseme channels, zero ancillary channels, each unchanged non-PP shared-opening coefficient, explicit PP closure, and inner-jaw pitch are all asserted. The previous `1e-5f` tolerance is retained; no viseme is excluded and no tolerance is relaxed.

One additional regression, `sourcePoseBridgePreservesMeasuredAudioWhileMapperClosesBilabialOnset`, exercises the actual classes:

1. A real `LipSyncFaceSource` receives synthetic measured level 0.8 and AA. Its normal smoothing/crossfade is sampled at 0 and 300 ms.
2. `applyTo` transfers AA, its weight, measured raw jaw and envelope into the pose. The mapper's full vowel output, shared opening and inner jaw are checked, including equality of source and renderer JawOpen for the vowel.
3. The source changes to PP and is sampled at 400 ms. The bridge must preserve raw jaw 1 and speaking energy above 0.79; neither is erased merely to make the renderer assertion pass.
4. That PP pose is rendered at the same presentation timestamp as the preceding AA pose. The expected entire weight array contains only PP=1, with zero outgoing AA and zero JawOpen; inner-jaw pitch is exactly zero.

The source's existing direct PP output still contains the raw jaw opening, which the test documents explicitly. The renderer is the location of the new lip-seal rule. This preserves the source/bridge contract and verifies the renderer's additional policy rather than falsely asserting identical behavior for two different paths.

## Evidence and next gate

- `original-red-LipSyncFaceSourceTest.xml`: unmodified copy of Root's actual failing class result.
- `production-callers.txt`: immutable-SHA caller search.
- `visible-render-path.txt`: actual direct View → Filament → Mapper calls.
- `manifest.json`: source/test hashes, exact commits, counts and execution boundaries.
- `one-file-test.patch`: the complete committed follow-up patch.

This class now has ten JUnit methods, previously nine. The child has not executed the corrected class. Root retains the original 167/1 run and performs the coordinated V2 test run against the combined source. No GPU, device, audible TTS or new live connection is claimed. Rollback is a scoped revert of this one-file test commit; the original red evidence remains preserved.
