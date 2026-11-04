# Bot 4 — reference cockpit completion

SOURCE_INTEGRATED / HOST_TESTED / LOCAL_ONLY. Recovered base ad68e9a8cdfba50e31e99906a4eca37f6130b155.
Original phone 59357, selector 59360 and open Fold 59358 inspected. Private originals excluded.

Existing candidate preserved: real canonical dashboard, same original animated orb, same avatar/pose events and lifecycle. Adds direct Chat / Agents / Bots / Auftrag tiles and three accessible Avatar / Kugel / Kompakt controls. Compact mode reduces stage height; no extra renderer, worker, model or memory. Existing 3D-vs-2D choice restored when switching back to Avatar.

Relevant EXP-05/11/12: UI only; decoder/S01, canonical work records, owner gates and identity stores unchanged. No execution, model run, new permission, install or release.

Coordinator AgentActivity wiring required in openHomeSlot:

```kotlin
at.heinz.deviceai.ui.ContinuumHomeLayout.Slot.ACTION_BOTS -> showBotRoster()
at.heinz.deviceai.ui.ContinuumHomeLayout.Slot.ACTION_TASK -> showCodingTaskDraftDialog()
```

Focused tests and native captures passed as detailed below. Coordinator Activity wiring/composer and Filament GPU/device acceptance remain open.
Rollback: revert this branch's commits. Original worktrees untouched.

## Verified checkpoint — 2026-11-04

Source integrated and host tested; LOCAL_ONLY / NOT_PUSHED (coordinator publishes via connector). Android `compileDebugKotlin`, `compileDebugJavaWithJavac`, unit/widget compilation and focused test task completed successfully.

20 tests passed, 0 failed, 0 skipped: ReferenceCockpitLayout 6, real Activity ReferenceCockpit 6, AdaptiveAvatarNavigation 5, AdaptiveCockpit 3. Phone 280/412dp and Fold 840dp portrait/landscape checked at 100/150/200% font; matrix geometry/text tests include wider sizes. Actual pointer events traverse decor/scroll hierarchy, open memory/bot/system destinations and real editor, retain unsent drafts, and preserve exactly one avatar when returning home. Selector test physically taps original orb, compact and avatar, verifying selected state, reduced height, saved preference and no task execution. Large-type card text measured with actual Android font metrics for overlap/clipping. A 320px-high Fold window keeps all eight rail targets scrollable.

Exact command (from this worktree):

```sh
python /workspace/scratch/391c0f6de827/build-env/run_gradle.py docs/evidence/device-experience-resume-20261104/layout/focused-gradle.log --offline -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir=/workspace/scratch/91435c40b766/bot04-layout/docs/evidence/device-experience-resume-20261104/layout :app:testDebugUnitTest --tests at.heinz.deviceai.ui.ContinuumReferenceCockpitLayoutTest --tests at.heinz.deviceai.ContinuumReferenceCockpitUiTest --tests at.heinz.deviceai.ContinuumAdaptiveCockpitUiTest --tests at.heinz.deviceai.ContinuumAdaptiveAvatarNavigationUiTest
```

Final result `BUILD SUCCESSFUL in 52s`. Raw JUnit XML, log and exact tested source SHA256 manifest accompany this report. Initial run exposed a superseded test assumption that every home control delegates to Activity; view selectors correctly act within the home. Its failing log is retained; the generic test now checks only destination delegation, and selectors have separate actual pointer/identity coverage.

54 real native Canvas PNGs generated; nine representative captures and view trees committed. Visually inspected phone/default, original orb/compact, open Fold/default, Fold portrait/200%, narrow home/200% and narrow chat/200%. Existing 2D fallback has flat face linework; these screenshots do **not** establish the desired dimensional Filament material/asset. Bot5 owns that renderer work and physical/GPU acceptance stays open.

## Coordinator integration still required

1. Apply `activity-wiring.patch`: Bots to existing roster; Auftrag to existing draft dialog. Those newly added callbacks cannot be verified before coordinator applies the two lines. Existing Agents destination already works.
2. Existing narrow/large-font Activity composer problem is visible in `narrow-280x720-font200-chat.png`: editor squeezed between fixed side controls, send row below bottom dock. `activity-composer-review.patch` supplies a bounded proposal giving input the whole row, max2 lines, accessible 48dp primary toolbar, natural-width scrollable secondary tools at width<360 or font>1.3. **Not applied or tested by Bot4**, because Activity is coordinator-owned. Final composer/send acceptance requires integration tests.
3. Inspect real Android Filament/device rendering; no camera, mic permission, model inference, APK/signing/install, release, worker or memory changes occurred here.
