# C1-UI-020 semantic test merge

**TEST_ONLY / REQUIRES_C1_COMPACT_MORE_PRODUCTION**

Base: `f274f7daf920cb34b0241881f343b85732f58ee1`. Frozen input: local C1 `f31855138d77ecb5e05e7a17a620b1fd6e9d28c5`, published as `c65604c85855a8dfb2e17617feafe83b7d560115`. The published Reference test blob was read back and matches `315f42d32518f0c8de4a73cb46c349bd8dbb04d6`. Only that frozen C1 test file was imported; no foreign working-tree file or product patch was used.

## Prepared change

One `ContinuumReferenceCockpitUiTest` now retains all eight frozen active-shell acceptance methods, the separately named `legacyReferencePresentationControlsReuseTheLiveAvatarAndPersistCompactChoice`, and our existing `shellAppearanceChooserReusesTheLiveAvatarAndPersistsTheChoice`, plus two new wide Compact cases: **12 distinct Reference cases**. The separate `ContinuumAvatarSlotLifecycleUiTest` stays byte-identical with its three cases. No production file changed.

Both restored-state scenarios (composer draft and active compact appearance) use the existing real `Bundle` path: pause/stop/save/destroy the old controller, then create a new Activity with `setup(savedState)`, advance the bounded frame clock, require attached decor, deliver focus, and close the new controller in `finally`. No `controller.recreate()` shortcut remains. The shared initial fixture retains the attached-window check and `controller.close()` cleanup.

The native ListView pressed-state duration is preserved in the current chooser case and added to C1's common native-dialog row helper. Every test still reaches actions through actual decor touches. Native 48dp minimums, unclipped target checks, all visible global-control intersections and the independent clipped rail-viewport-versus-shell check remain enforced. Both rectangles in that additional check now come directly from `getGlobalVisibleRect`; no local-to-parent conversion is used. This incorporates the parent-measured animation-coordinate correction from `681b6f1` (global bounds retain a 4px gap while the prior local conversion falsely overlapped). C1's global pairwise assertions and all sibling checks are unchanged. Legacy captures have distinct names so they cannot overwrite active-shell compact evidence. Only the explicitly named legacy fixture selects the legacy presentation.

## Required production connection

This preparatory commit is not ready for standalone acceptance on its base. C1 must publish its finished, pinned product connection for the native fourth `Kompakt · Kugel` choice, active shell/avatar/content geometry and restoration, and the wide Systemstatus → Mehr → Entwicklerwerkzeuge → Coding draft route. Parent integrates that completed product change together with this test merge while retaining the R3 avatar ownership/callback guards and the corrected 48dp status targets. The same existing shell, avatar surface and More menu remain authoritative.

## Verification boundary

`git diff --check` and structural source checks passed: twelve unique Reference methods, all ten prior methods byte-unchanged, all eight frozen methods and their labelled assertion messages retained, two explicit saved-state restore scenarios, native ListView delays, strict size/clipped-bounds checks, and unchanged three-case lifecycle-test blob. Exact identities and method names are in `UI020-TEST-MERGE.json`. These checks are source inspection, not compilation or test execution.

**Gradle / Android compilation / Android tests: NOT_RUN in this worker.** Parent owns actual combined execution after the finished C1 product input is pinned. Expected focused scope: 12 Reference cases plus the unchanged 3 AvatarLifecycle cases, followed by the canonical shared gates.

## Wide Compact UI extension

Appended on `b3b791ae6f1f8482bbcca4ce2b46bfb9ad2f79eb` after the parent identified the distinct wide Compact geometry in product commit `7e33883543389e487e30aa3c7d83c2afa028df92`. That product commit was read only; it was not imported here. The two new UI methods cover 840×649 landscape and 840×1050 portrait, each at font scales 1.0, 1.5 and 2.0. All ten existing methods and helpers remain byte-identical.

Both new methods use the existing actual-Activity/decor-touch helpers to select the fourth native choice. They require a smaller but identical visible avatar surface, higher conversation target, shorter actual content, matching renderer slot and persisted compact selection. Normal, compact and restored states all run the complete strict 48dp/bounds/global-overlap checks. Selecting the existing 2D choice must restore exact avatar bounds, renderer bounds, conversation bounds and content width/height, with one surface and no task start throughout. This addresses view rounding and live hierarchy coverage that pure Kotlin geometry checks do not establish.

No prior assertion or test method was removed or edited. Worker runtime status remains **NOT_RUN**; the parent executes all 12 Reference cases against the combined product source.
